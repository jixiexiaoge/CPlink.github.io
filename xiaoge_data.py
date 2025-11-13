#!/usr/bin/env python3
"""
小鸽数据广播模块
从系统获取实时数据，通过UDP广播到7701端口
"""

import fcntl
import json  # 保留用于调试（可选）
import math
import socket
import struct
import time
import traceback
import zlib
from typing import Dict, Any, Optional, List, Tuple
import numpy as np

try:
    import msgpack
    MSGPACK_AVAILABLE = True
except ImportError:
    MSGPACK_AVAILABLE = False
    print("Warning: msgpack not available, falling back to JSON. Install with: pip install msgpack")

import cereal.messaging as messaging
from openpilot.common.realtime import Ratekeeper
from openpilot.system.hardware import PC


class XiaogeDataBroadcaster:
    def __init__(self):
        self.broadcast_port = 7701
        self.broadcast_ip = None
        self.sequence = 0

        # 初始化UDP socket
        self.udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

        # 订阅消息
        self.sm = messaging.SubMaster([
            'carState',
            'modelV2',
            'radarState',
            'selfdriveState',
            'controlsState',
            'longitudinalPlan',
            'lateralPlan',  # 添加 lateralPlan - 当 activeLaneLine 启用时，路径数据来自这里
            'carControl',   # 添加 carControl - 用于获取曲率信息
            'carrotMan',
            # 移除 'can' - 盲区数据直接从carState获取
        ])

        # 获取广播地址
        self.broadcast_ip = self.get_broadcast_address()
        if self.broadcast_ip == '255.255.255.255':
            print("Warning: Could not determine network interface, using fallback broadcast address")

    def get_broadcast_address(self):
        """获取广播地址"""
        interfaces = [b'br0', b'eth0', b'enp0s3'] if PC else [b'wlan0', b'eth0']

        for iface in interfaces:
            try:
                with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                    ip = fcntl.ioctl(
                        s.fileno(),
                        0x8919,  # SIOCGIFADDR
                        struct.pack('256s', iface)
                    )[20:24]
                    broadcast_ip = socket.inet_ntoa(ip)
                    ip_parts = broadcast_ip.split('.')
                    ip_parts[3] = '255'
                    return '.'.join(ip_parts)
            except (OSError, Exception):
                continue

        return '255.255.255.255'

    def _capnp_list_to_list(self, capnp_list, max_items=None):
        """将capnp列表转换为Python列表"""
        if capnp_list is None:
            return []
        try:
            result = [float(x) for x in capnp_list]
            if max_items is not None:
                return result[:max_items]
            return result
        except (TypeError, AttributeError):
            return []

    def _capnp_enum_to_int(self, enum_value):
        """将capnp枚举转换为整数"""
        try:
            return int(enum_value)
        except (TypeError, ValueError):
            return 0

    def _sample_array(self, arr: List[float], step: int = 2) -> List[float]:
        """
        对数组进行采样，减少数据量
        step=1: 不采样（全部保留）
        step=2: 每隔一个点取一个（保留50%）
        step=3: 每隔两个点取一个（保留33%）
        """
        if step <= 1 or len(arr) <= 1:
            return arr
        return arr[::step]

    def _calculate_curvature_from_path(self, x: List[float], y: List[float], sample: int = 4) -> Tuple[float, int]:
        """
        基于路径坐标计算曲率（参考 carrot_man.py:163-181）
        使用三点法计算曲率：p1, p2, p3
        
        Args:
            x: 路径的 x 坐标数组（距离）
            y: 路径的 y 坐标数组（横向偏移）
            sample: 采样间隔（用于选择三个点）
        
        Returns:
            (curvature, direction): 曲率值和方向（1=左转，-1=右转，0=直道）
        """
        if len(x) < sample * 2 + 1 or len(y) < sample * 2 + 1:
            return 0.0, 0
        
        # 选择三个点：起点、中间点、终点
        # 使用路径的前半部分计算曲率（更接近车辆当前位置）
        max_idx = min(len(x) - sample * 2, 10)  # 最多使用前10个点
        if max_idx < 1:
            return 0.0, 0
        
        # 选择三个点
        idx1 = 0
        idx2 = min(sample, max_idx - 1)
        idx3 = min(sample * 2, max_idx - 1)
        
        p1 = (x[idx1], y[idx1])
        p2 = (x[idx2], y[idx2])
        p3 = (x[idx3], y[idx3])
        
        # 计算向量
        v1 = (p2[0] - p1[0], p2[1] - p1[1])
        v2 = (p3[0] - p2[0], p3[1] - p2[1])
        
        # 计算叉积
        cross_product = v1[0] * v2[1] - v1[1] * v2[0]
        len_v1 = math.sqrt(v1[0] ** 2 + v1[1] ** 2)
        len_v2 = math.sqrt(v2[0] ** 2 + v2[1] ** 2)
        
        if len_v1 * len_v2 == 0:
            return 0.0, 0
        
        # 计算曲率
        curvature = cross_product / (len_v1 * len_v2 * len_v1)
        
        # 确定方向
        direction = 1 if curvature > 0 else (-1 if curvature < 0 else 0)
        
        return float(curvature), direction

    def collect_car_state(self, carState) -> Dict[str, Any]:
        """收集本车状态数据 - 优化版（移除冗余字段）"""
        return {
            'vEgo': float(carState.vEgo),  # 实际速度
            'aEgo': float(carState.aEgo),  # 加速度
            'steeringAngleDeg': float(carState.steeringAngleDeg),  # 方向盘角度
            'leftBlinker': bool(carState.leftBlinker),  # 转向灯
            'rightBlinker': bool(carState.rightBlinker),
            'brakePressed': bool(carState.brakePressed),  # 刹车
            'leftLatDist': float(carState.leftLatDist),  # 车道距离
            'rightLatDist': float(carState.rightLatDist),
            'leftLaneLine': int(carState.leftLaneLine),  # 车道线类型
            'rightLaneLine': int(carState.rightLaneLine),
            'standstill': bool(carState.standstill),  # 静止状态
            'leftBlindspot': bool(carState.leftBlindspot) if hasattr(carState, 'leftBlindspot') else False,  # 左盲区
            'rightBlindspot': bool(carState.rightBlindspot) if hasattr(carState, 'rightBlindspot') else False,  # 右盲区
            # 移除 vEgoCluster - 超车决策不需要仪表盘速度
            # 移除 vCruise - 使用 longitudinalPlan.cruiseTarget 代替
        }

    def collect_model_data(self, modelV2, lateralPlan=None, carControl=None, controlsState=None) -> Dict[str, Any]:
        """
        收集模型数据 - 优化版（移除冗余数据，只保留超车决策所需）
        
        Args:
            modelV2: ModelV2 消息对象
            lateralPlan: LateralPlan 消息对象（可选，当 activeLaneLine 启用时使用）
            carControl: CarControl 消息对象（可选，用于获取曲率）
            controlsState: ControlsState 消息对象（可选，用于判断 activeLaneLine）
        """
        data = {}

        # 前车检测 - 保留关键信息（距离、速度、加速度、置信度）
        if len(modelV2.leadsV3) > 0:
            lead0 = modelV2.leadsV3[0]
            data['lead0'] = {
                'x': float(lead0.x[0]) if len(lead0.x) > 0 else 0.0,  # 距离
                'v': float(lead0.v[0]) if len(lead0.v) > 0 else 0.0,  # 速度
                'a': float(lead0.a[0]) if len(lead0.a) > 0 else 0.0,  # 加速度（用于判断前车是否在减速）
                'prob': float(lead0.prob),  # 置信度
                # 移除 'y' (横向位置) - 超车决策不需要
            }
        else:
            data['lead0'] = {'x': 0.0, 'v': 0.0, 'a': 0.0, 'prob': 0.0}

        # 第二前车 - 添加速度信息（用于判断超车空间）
        if len(modelV2.leadsV3) > 1:
            lead1 = modelV2.leadsV3[1]
            data['lead1'] = {
                'x': float(lead1.x[0]) if len(lead1.x) > 0 else 0.0,  # 距离
                'v': float(lead1.v[0]) if len(lead1.v) > 0 else 0.0,  # 速度（判断超车空间）
                'prob': float(lead1.prob),  # 置信度
            }
        else:
            data['lead1'] = {'x': 0.0, 'v': 0.0, 'prob': 0.0}

        # 🎯 车道线数据 - 发送完整的4条车道线坐标和置信度（采样以减少数据量）
        # 车道线索引：0=最左侧, 1=左车道线, 2=右车道线, 3=最右侧
        lane_lines = modelV2.laneLines
        data['laneLines'] = []
        # 采样步长：每隔一个点取一个（减少50%数据量）
        sample_step = 2
        for i in range(min(4, len(lane_lines))):
            lane_line = lane_lines[i]
            x_list = self._capnp_list_to_list(lane_line.x)
            y_list = self._capnp_list_to_list(lane_line.y)
            z_list = self._capnp_list_to_list(lane_line.z)
            data['laneLines'].append({
                'x': self._sample_array(x_list, sample_step),  # 距离数组（米）- 采样
                'y': self._sample_array(y_list, sample_step),  # 横向偏移数组（米）- 采样
                'z': self._sample_array(z_list, sample_step),  # 高度数组（米）- 采样
            })
        # 如果少于4条，用空数据填充
        while len(data['laneLines']) < 4:
            data['laneLines'].append({'x': [], 'y': [], 'z': []})
        
        # 车道线置信度 - 发送4个值
        if len(modelV2.laneLineProbs) >= 4:
            data['laneLineProbs'] = [
                float(modelV2.laneLineProbs[0]),  # 最左侧车道线置信度
                float(modelV2.laneLineProbs[1]),  # 左车道线置信度
                float(modelV2.laneLineProbs[2]),  # 右车道线置信度
                float(modelV2.laneLineProbs[3]),  # 最右侧车道线置信度
            ]
        elif len(modelV2.laneLineProbs) >= 2:
            # 兼容旧版本：只有2个值，填充为4个
            data['laneLineProbs'] = [
                0.0,  # 最左侧（未知）
                float(modelV2.laneLineProbs[0]),  # 左车道线
                float(modelV2.laneLineProbs[1]),  # 右车道线
                0.0,  # 最右侧（未知）
            ]
        else:
            data['laneLineProbs'] = [0.0, 0.0, 0.0, 0.0]

        # 车道宽度、到路边缘距离和变道状态 - 保留（超车决策需要）
        meta = modelV2.meta
        data['meta'] = {
            'laneWidthLeft': float(meta.laneWidthLeft),  # 左车道宽度
            'laneWidthRight': float(meta.laneWidthRight),  # 右车道宽度
            'distanceToRoadEdgeLeft': float(meta.distanceToRoadEdgeLeft) if hasattr(meta, 'distanceToRoadEdgeLeft') else 0.0,  # 到左路边缘距离
            'distanceToRoadEdgeRight': float(meta.distanceToRoadEdgeRight) if hasattr(meta, 'distanceToRoadEdgeRight') else 0.0,  # 到右路边缘距离
            'laneChangeState': self._capnp_enum_to_int(meta.laneChangeState),
            'laneChangeDirection': self._capnp_enum_to_int(meta.laneChangeDirection),
        }

        # 🎯 曲率信息 - 优先使用 carControl.actuators.curvature，否则基于路径计算
        curvature_value = 0.0
        curvature_direction = 0
        curvature_obtained = False  # 标记是否已成功获取曲率
        
        # 方法1：优先从 carControl 获取曲率（最准确）
        if carControl and hasattr(carControl, 'actuators'):
            try:
                actuators = carControl.actuators
                if hasattr(actuators, 'curvature'):
                    curvature_value = float(actuators.curvature)
                    curvature_direction = 1 if curvature_value > 0 else (-1 if curvature_value < 0 else 0)
                    curvature_obtained = True  # 标记已获取（即使值为0，也表示是有效的直道数据）
            except (AttributeError, TypeError):
                pass
        
        # 方法2：如果 carControl 没有曲率，尝试从 orientationRate 获取（保持向后兼容）
        if not curvature_obtained:
            if hasattr(modelV2, 'orientationRate') and len(modelV2.orientationRate.z) > 0:
                orientation_rate_z = self._capnp_list_to_list(modelV2.orientationRate.z)
                if orientation_rate_z:
                    # 找到最大方向变化率（表示最大曲率点）
                    max_index = max(range(len(orientation_rate_z)), key=lambda i: abs(orientation_rate_z[i]))
                    max_orientation_rate = orientation_rate_z[max_index]
                    curvature_value = float(max_orientation_rate)
                    curvature_direction = 1 if max_orientation_rate > 0 else -1
                    curvature_obtained = True
        
        # 方法3：如果前两种方法都失败，基于路径坐标计算曲率
        if not curvature_obtained:
            # 获取路径数据（优先使用 lateralPlan，否则使用 modelV2）
            position = None
            if lateralPlan and hasattr(lateralPlan, 'position'):
                position = lateralPlan.position
            elif hasattr(modelV2, 'position'):
                position = modelV2.position
            
            if position:
                x_list = self._capnp_list_to_list(position.x)
                y_list = self._capnp_list_to_list(position.y)
                if len(x_list) >= 3 and len(y_list) >= 3:
                    curvature_value, curvature_direction = self._calculate_curvature_from_path(x_list, y_list)
                    curvature_obtained = True
        
        data['curvature'] = {
            'maxOrientationRate': curvature_value,  # 曲率值（可能是 curvature 或 orientationRate）
            'direction': curvature_direction,  # 方向：1=左转，-1=右转，0=直道
        }

        # 🎯 路缘线数据 - 发送2条路缘线的坐标和标准差（采样以减少数据量）
        if hasattr(modelV2, 'roadEdges') and len(modelV2.roadEdges) >= 2:
            road_edges = modelV2.roadEdges
            road_edge_stds = modelV2.roadEdgeStds if hasattr(modelV2, 'roadEdgeStds') else []
            
            data['roadEdges'] = []
            sample_step = 2  # 采样步长
            for i in range(min(2, len(road_edges))):
                road_edge = road_edges[i]
                x_list = self._capnp_list_to_list(road_edge.x)
                y_list = self._capnp_list_to_list(road_edge.y)
                z_list = self._capnp_list_to_list(road_edge.z)
                data['roadEdges'].append({
                    'x': self._sample_array(x_list, sample_step),  # 采样
                    'y': self._sample_array(y_list, sample_step),  # 采样
                    'z': self._sample_array(z_list, sample_step),  # 采样
                })
            # 如果少于2条，用空数据填充
            while len(data['roadEdges']) < 2:
                data['roadEdges'].append({'x': [], 'y': [], 'z': []})
            
            # 路缘线标准差
            if len(road_edge_stds) >= 2:
                data['roadEdgeStds'] = [
                    float(road_edge_stds[0]),
                    float(road_edge_stds[1]),
                ]
            else:
                data['roadEdgeStds'] = [0.0, 0.0]
        else:
            data['roadEdges'] = [
                {'x': [], 'y': [], 'z': []},
                {'x': [], 'y': [], 'z': []},
            ]
            data['roadEdgeStds'] = [0.0, 0.0]

        # 🎯 路径引导数据 - 优先使用 lateralPlan.position（当 useLaneLines 启用时）
        # 参考 carrot.cc:1646-1650 和 lateral_planner.py:231-243
        position = None
        use_lane_lines = False
        
        # 检查是否启用了车道线模式（优先使用 lateralPlan.useLaneLines）
        if lateralPlan and hasattr(lateralPlan, 'useLaneLines'):
            use_lane_lines = bool(lateralPlan.useLaneLines)
        elif controlsState and hasattr(controlsState, 'activeLaneLine'):
            # 备选方案：使用 controlsState.activeLaneLine（向后兼容）
            use_lane_lines = bool(controlsState.activeLaneLine)
        
        # 根据 useLaneLines 状态选择数据源
        if use_lane_lines and lateralPlan and hasattr(lateralPlan, 'position'):
            # 当 useLaneLines 启用时，使用 lateralPlan.position
            position = lateralPlan.position
        elif hasattr(modelV2, 'position'):
            # 否则使用 modelV2.position
            position = modelV2.position
        
        if position:
            x_list = self._capnp_list_to_list(position.x)
            y_list = self._capnp_list_to_list(position.y)
            z_list = self._capnp_list_to_list(position.z)
            # 采样以减少数据量
            sample_step = 2
            data['position'] = {
                'x': self._sample_array(x_list, sample_step),
                'y': self._sample_array(y_list, sample_step),
                'z': self._sample_array(z_list, sample_step),
            }
        else:
            data['position'] = {'x': [], 'y': [], 'z': []}

        return data

    def collect_radar_data(self, radarState) -> Dict[str, Any]:
        """收集雷达数据（纯视觉方案也会生成这些数据）"""
        data = {}

        # leadOne信息
        leadOne = radarState.leadOne
        data['leadOne'] = {
            'dRel': float(leadOne.dRel),
            'vRel': float(leadOne.vRel),
            'vLead': float(leadOne.vLead),
            'vLeadK': float(leadOne.vLeadK),
            'status': bool(leadOne.status),
        }

        # leadTwo信息
        if hasattr(radarState, 'leadTwo'):
            leadTwo = radarState.leadTwo
            data['leadTwo'] = {
                'dRel': float(leadTwo.dRel),
                'status': bool(leadTwo.status),
            }
        else:
            data['leadTwo'] = {'dRel': 0.0, 'status': False}

        # 侧方车辆信息
        if hasattr(radarState, 'leadLeft'):
            leadLeft = radarState.leadLeft
            data['leadLeft'] = {
                'dRel': float(leadLeft.dRel) if leadLeft.status else 0.0,
                'vRel': float(leadLeft.vRel) if leadLeft.status else 0.0,
                'status': bool(leadLeft.status),
            }
        else:
            data['leadLeft'] = {'dRel': 0.0, 'vRel': 0.0, 'status': False}

        if hasattr(radarState, 'leadRight'):
            leadRight = radarState.leadRight
            data['leadRight'] = {
                'dRel': float(leadRight.dRel) if leadRight.status else 0.0,
                'vRel': float(leadRight.vRel) if leadRight.status else 0.0,
                'status': bool(leadRight.status),
            }
        else:
            data['leadRight'] = {'dRel': 0.0, 'vRel': 0.0, 'status': False}

        return data

    def collect_system_state(self, selfdriveState, controlsState) -> Dict[str, Any]:
        """收集系统状态"""
        return {
            'enabled': bool(selfdriveState.enabled) if selfdriveState else False,
            'active': bool(selfdriveState.active) if selfdriveState else False,
            'longControlState': self._capnp_enum_to_int(controlsState.longControlState) if controlsState else 0,
        }

    def collect_carrot_data(self, carrotMan) -> Dict[str, Any]:
        """收集 carrot 导航和限速数据"""
        return {
            'nRoadLimitSpeed': int(carrotMan.nRoadLimitSpeed) if hasattr(carrotMan, 'nRoadLimitSpeed') else 0,
            'desiredSpeed': int(carrotMan.desiredSpeed) if hasattr(carrotMan, 'desiredSpeed') else 0,
            'xSpdLimit': int(carrotMan.xSpdLimit) if hasattr(carrotMan, 'xSpdLimit') else 0,
            'xSpdDist': int(carrotMan.xSpdDist) if hasattr(carrotMan, 'xSpdDist') else 0,
            'xSpdType': int(carrotMan.xSpdType) if hasattr(carrotMan, 'xSpdType') else 0,
            'roadcate': int(carrotMan.roadcate) if hasattr(carrotMan, 'roadcate') else 0,  # 道路类型（高速/快速路/城市道路）
        }

    # 移除 collect_blindspot_data() - 盲区数据已直接从carState获取

    def create_packet(self, data: Dict[str, Any]) -> bytes:
        """
        创建数据包，包含序列号、时间戳和校验
        使用 msgpack 序列化（比 JSON 减少约30%大小，速度提升2-3倍）
        """
        packet_data = {
            'version': 1,
            'sequence': self.sequence,
            'timestamp': time.time() * 1000,  # 🔧 转换为毫秒（匹配安卓端期望）
            'data': data
        }

        # 使用 msgpack 替代 JSON（减少约30%大小，速度提升2-3倍）
        # use_bin_type=True 确保字节数据被正确编码，对跨语言通信很重要
        if MSGPACK_AVAILABLE:
            try:
                packet_bytes = msgpack.packb(packet_data, use_bin_type=True)
            except Exception as e:
                print(f"Warning: msgpack serialization failed, falling back to JSON: {e}")
                # 回退到 JSON
                json_str = json.dumps(packet_data)
                packet_bytes = json_str.encode('utf-8')
        else:
            # 回退到 JSON（如果 msgpack 不可用）
            json_str = json.dumps(packet_data)
            packet_bytes = json_str.encode('utf-8')

        # 添加CRC32校验
        checksum = zlib.crc32(packet_bytes) & 0xffffffff

        # 数据包格式: [校验和(4字节)][数据长度(4字节)][数据]
        packet = struct.pack('!II', checksum, len(packet_bytes)) + packet_bytes

        # 检查数据包大小
        # 注意：经过采样优化和 msgpack 序列化后，数据包大小应该显著减小
        # 如果仍然超过 1400 字节，可能需要进一步采样（增加 sample_step）或实现分包发送
        if len(packet) > 1400:  # 留一些余量，避免超过MTU
            print(f"Warning: Packet size {len(packet)} bytes may exceed MTU (consider increasing sample_step)")

        return packet

    def broadcast_data(self):
        """主循环：收集数据并广播"""
        rk = Ratekeeper(20, print_delay_threshold=None)  # 20Hz

        print(f"XiaogeDataBroadcaster started, broadcasting to {self.broadcast_ip}:{self.broadcast_port}")

        while True:
            try:
                # 性能监控
                start_time = time.perf_counter()

                # 更新所有消息
                self.sm.update(0)

                # 收集数据
                data = {}

                # 本车状态 - 始终收集
                if self.sm.alive['carState']:
                    car_state = self.collect_car_state(self.sm['carState'])
                    # 数据验证
                    if car_state.get('vEgo', 0) < 0:
                        print("Warning: Invalid vEgo value detected")
                    data['carState'] = car_state

                # 模型数据 - 传入 lateralPlan 和 carControl 以支持完整功能
                if self.sm.alive['modelV2']:
                    lateral_plan = self.sm['lateralPlan'] if self.sm.alive['lateralPlan'] else None
                    car_control = self.sm['carControl'] if self.sm.alive['carControl'] else None
                    controls_state = self.sm['controlsState'] if self.sm.alive['controlsState'] else None
                    data['modelV2'] = self.collect_model_data(
                        self.sm['modelV2'],
                        lateralPlan=lateral_plan,
                        carControl=car_control,
                        controlsState=controls_state
                    )

                # 雷达数据（纯视觉方案也会有）
                if self.sm.alive['radarState']:
                    data['radarState'] = self.collect_radar_data(self.sm['radarState'])

                # 系统状态
                if self.sm.alive['selfdriveState'] and self.sm.alive['controlsState']:
                    data['systemState'] = self.collect_system_state(
                        self.sm['selfdriveState'],
                        self.sm['controlsState']
                    )

                # 纵向规划数据
                if self.sm.alive['longitudinalPlan']:
                    lp = self.sm['longitudinalPlan']
                    data['longitudinalPlan'] = {
                        'xState': self._capnp_enum_to_int(lp.xState),
                        'trafficState': self._capnp_enum_to_int(lp.trafficState),
                        'cruiseTarget': float(lp.cruiseTarget),
                        'hasLead': bool(lp.hasLead),
                    }

                # carrot 导航和限速数据
                if self.sm.alive['carrotMan']:
                    data['carrotMan'] = self.collect_carrot_data(self.sm['carrotMan'])

                # 盲区数据已包含在carState中，无需单独收集

                # 性能监控
                processing_time = time.perf_counter() - start_time
                if processing_time > 0.05:  # 超过50ms
                    print(f"Warning: Slow processing detected: {processing_time*1000:.1f}ms")

                # 如果有数据则广播
                if data:
                    packet = self.create_packet(data)

                    try:
                        self.udp_socket.sendto(packet, (self.broadcast_ip, self.broadcast_port))
                        self.sequence += 1

                        # 每100帧打印一次日志（包含数据统计信息）
                        if self.sequence % 100 == 0:
                            # 计算数据统计信息
                            stats_info = []
                            if 'modelV2' in data:
                                model_data = data['modelV2']
                                if 'laneLines' in model_data and len(model_data['laneLines']) > 0:
                                    lane_line_points = len(model_data['laneLines'][0]['x'])
                                    stats_info.append(f"laneLines: {lane_line_points}pts/line")
                                if 'roadEdges' in model_data and len(model_data['roadEdges']) > 0:
                                    road_edge_points = len(model_data['roadEdges'][0]['x'])
                                    stats_info.append(f"roadEdges: {road_edge_points}pts/edge")
                                if 'position' in model_data:
                                    position_points = len(model_data['position']['x'])
                                    stats_info.append(f"position: {position_points}pts")
                            
                            # 输出日志（显示序列化格式）
                            format_type = "msgpack" if MSGPACK_AVAILABLE else "JSON"
                            stats_str = f" ({', '.join(stats_info)})" if stats_info else ""
                            print(f"Broadcasted {self.sequence} packets ({format_type}), last size: {len(packet)} bytes{stats_str}")
                    except Exception as e:
                        print(f"Failed to broadcast packet: {e}")

                rk.keep_time()

            except Exception as e:
                print(f"XiaogeDataBroadcaster error: {e}")
                traceback.print_exc()
                time.sleep(1)


def main():
    broadcaster = XiaogeDataBroadcaster()
    broadcaster.broadcast_data()


if __name__ == "__main__":
    main()