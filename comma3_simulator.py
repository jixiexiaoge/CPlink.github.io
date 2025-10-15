#!/usr/bin/env python3
"""
Comma3 Device Simulator for Windows 11
Comprehensive Python simulator that mimics comma3 device functionality for CarrotAmap testing

Features:
- UDP broadcast discovery (port 7705)
- Main data communication (port 7706) 
- Route data transmission (port 7709)
- ZMQ command interface (port 7710)
- KISA data support (port 12345)
- Real-time GUI interface
- Vehicle data simulation
- Protocol compliance with CarrotMan

Author: Augment Agent
Date: 2025-01-20
"""

import json
import socket
import struct
import threading
import time
import tkinter as tk
from tkinter import ttk, scrolledtext
import traceback
from datetime import datetime
from typing import Dict, Any, List, Tuple
import math
import random
from collections import OrderedDict

class RealtimeDataWindow:
    """独立的实时数据窗口"""
    def __init__(self, parent_simulator):
        self.parent = parent_simulator
        self.window = None
        self.is_paused = False
        self.field_order = OrderedDict()  # 保持字段顺序
        self.field_widgets = {}  # 存储字段对应的widget
        self.last_values = {}  # 存储上次的值，用于防闪烁
        
    def create_window(self):
        """创建独立窗口"""
        if self.window is not None:
            self.window.lift()
            return
            
        self.window = tk.Toplevel()
        self.window.title("实时导航数据 - Comma3 模拟器")
        self.window.geometry("1000x700")
        self.window.state('zoomed')  # 最大化窗口
        
        # 设置窗口图标和属性
        try:
            self.window.iconbitmap(default="")
        except:
            pass
            
        # 创建主框架
        main_frame = ttk.Frame(self.window)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # 控制面板
        self.setup_control_panel(main_frame)
        
        # 数据表格
        self.setup_data_table(main_frame)
        
        # 状态栏
        self.setup_status_bar(main_frame)
        
        # 绑定窗口关闭事件
        self.window.protocol("WM_DELETE_WINDOW", self.on_closing)
        
    def setup_control_panel(self, parent):
        """设置控制面板"""
        control_frame = ttk.LabelFrame(parent, text="控制面板")
        control_frame.pack(fill=tk.X, pady=(0, 10))
        
        # 暂停按钮
        self.pause_btn = ttk.Button(control_frame, text="⏸️ 暂停更新", 
                                   command=self.toggle_pause)
        self.pause_btn.pack(side=tk.LEFT, padx=5)
        
        # 导出按钮
        self.export_btn = ttk.Button(control_frame, text="📁 导出数据", 
                                    command=self.export_data)
        self.export_btn.pack(side=tk.LEFT, padx=5)
        
        # 清空按钮
        self.clear_btn = ttk.Button(control_frame, text="🗑️ 清空数据", 
                                   command=self.clear_data)
        self.clear_btn.pack(side=tk.LEFT, padx=5)
        
        # 刷新按钮
        self.refresh_btn = ttk.Button(control_frame, text="🔄 刷新显示", 
                                      command=self.refresh_display)
        self.refresh_btn.pack(side=tk.LEFT, padx=5)
        
        # 状态标签
        self.status_label = ttk.Label(control_frame, text="状态: 运行中")
        self.status_label.pack(side=tk.RIGHT, padx=5)
        
    def setup_data_table(self, parent):
        """设置数据表格"""
        # 创建表格框架
        table_frame = ttk.LabelFrame(parent, text="实时导航数据")
        table_frame.pack(fill=tk.BOTH, expand=True)
        
        # 创建Treeview
        columns = ("序号", "字段名称", "原始字段名", "当前值", "数据类型", "分类", "更新时间", "状态")
        self.tree = ttk.Treeview(table_frame, columns=columns, show="headings", height=25)
        
        # 配置列
        column_config = {
            "序号": 50,
            "字段名称": 120,
            "原始字段名": 120,
            "当前值": 150,
            "数据类型": 80,
            "分类": 100,
            "更新时间": 100,
            "状态": 80
        }
        
        for col in columns:
            self.tree.heading(col, text=col, anchor=tk.W)
            self.tree.column(col, width=column_config.get(col, 100), anchor=tk.W)
        
        # 添加滚动条
        v_scrollbar = ttk.Scrollbar(table_frame, orient=tk.VERTICAL, command=self.tree.yview)
        h_scrollbar = ttk.Scrollbar(table_frame, orient=tk.HORIZONTAL, command=self.tree.xview)
        self.tree.configure(yscrollcommand=v_scrollbar.set, xscrollcommand=h_scrollbar.set)
        
        # 布局
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        v_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        h_scrollbar.pack(side=tk.BOTTOM, fill=tk.X)
        
        # 配置标签颜色
        self.tree.tag_configure("new", background="#e8f5e8")
        self.tree.tag_configure("updated", background="#fff2cc")
        self.tree.tag_configure("normal", background="white")
        self.tree.tag_configure("missing_required", background="#ffebee", foreground="#d32f2f")
        self.tree.tag_configure("missing_important", background="#fff3e0", foreground="#f57c00")
        
    def setup_status_bar(self, parent):
        """设置状态栏"""
        status_frame = ttk.Frame(parent)
        status_frame.pack(fill=tk.X, pady=(10, 0))
        
        self.info_label = ttk.Label(status_frame, text="准备就绪")
        self.info_label.pack(side=tk.LEFT)
        
        self.count_label = ttk.Label(status_frame, text="字段数: 0")
        self.count_label.pack(side=tk.RIGHT)
        
    def toggle_pause(self):
        """切换暂停状态"""
        self.is_paused = not self.is_paused
        if self.is_paused:
            self.pause_btn.config(text="▶️ 继续更新")
            self.status_label.config(text="状态: 已暂停")
            self.info_label.config(text="显示已暂停，可以检查数据字段")
        else:
            self.pause_btn.config(text="⏸️ 暂停更新")
            self.status_label.config(text="状态: 运行中")
            self.info_label.config(text="显示已恢复")
            
    def export_data(self):
        """导出数据"""
        try:
            from tkinter import filedialog
            import csv
            
            filename = filedialog.asksaveasfilename(
                defaultextension=".csv",
                filetypes=[
                    ("CSV files", "*.csv"),
                    ("Text files", "*.txt"),
                    ("JSON files", "*.json"),
                    ("All files", "*.*")
                ],
                title="导出实时数据"
            )
            
            if filename:
                if filename.endswith('.csv'):
                    self.export_to_csv(filename)
                elif filename.endswith('.json'):
                    self.export_to_json(filename)
                else:
                    self.export_to_text(filename)
                    
        except Exception as e:
            self.info_label.config(text=f"导出失败: {e}")
            
    def export_to_csv(self, filename):
        """导出为CSV格式"""
        try:
            import csv
            with open(filename, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                writer.writerow(['序号', '字段名称', '原始字段名', '当前值', '数据类型', '分类', '更新时间', '状态'])
                
                for item in self.tree.get_children():
                    values = self.tree.item(item)['values']
                    writer.writerow(values)
                    
            self.info_label.config(text=f"CSV数据已导出: {filename}")
        except Exception as e:
            self.info_label.config(text=f"CSV导出失败: {e}")
            
    def export_to_json(self, filename):
        """导出为JSON格式"""
        try:
            import json
            data = {
                "export_time": datetime.now().isoformat(),
                "fields": []
            }
            
            for item in self.tree.get_children():
                values = self.tree.item(item)['values']
                field_data = {
                    "index": values[0],
                    "display_name": values[1],
                    "original_name": values[2],
                    "current_value": values[3],
                    "data_type": values[4],
                    "category": values[5],
                    "update_time": values[6],
                    "status": values[7]
                }
                data["fields"].append(field_data)
                
            with open(filename, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                
            self.info_label.config(text=f"JSON数据已导出: {filename}")
        except Exception as e:
            self.info_label.config(text=f"JSON导出失败: {e}")
            
    def export_to_text(self, filename):
        """导出为文本格式"""
        try:
            with open(filename, 'w', encoding='utf-8') as f:
                f.write("=== 实时导航数据导出 ===\n")
                f.write(f"导出时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write(f"字段总数: {len(self.tree.get_children())}\n\n")
                
                for item in self.tree.get_children():
                    values = self.tree.item(item)['values']
                    f.write(f"{values[0]}. {values[1]} ({values[2]})\n")
                    f.write(f"   值: {values[3]}\n")
                    f.write(f"   类型: {values[4]}, 分类: {values[5]}\n")
                    f.write(f"   时间: {values[6]}, 状态: {values[7]}\n\n")
                    
            self.info_label.config(text=f"文本数据已导出: {filename}")
        except Exception as e:
            self.info_label.config(text=f"文本导出失败: {e}")
            
    def clear_data(self):
        """清空数据"""
        for item in self.tree.get_children():
            self.tree.delete(item)
        self.field_order.clear()
        self.field_widgets.clear()
        self.last_values.clear()
        self.count_label.config(text="字段数: 0")
        self.info_label.config(text="数据已清空")
        
    def refresh_display(self):
        """刷新显示"""
        if not self.is_paused and hasattr(self.parent, 'current_navigation_data'):
            self.update_display(self.parent.current_navigation_data)
            
    def update_display(self, data):
        """更新显示 - 防闪烁优化"""
        if self.is_paused or not self.window:
            return
            
        try:
            current_time = datetime.now().strftime("%H:%M:%S")
            field_defs = self.parent.get_navigation_field_definitions()
            
            # 按顺序处理字段
            for field, value in data.items():
                self.update_field_row(field, value, current_time, field_defs)
                
            # 更新统计信息
            self.count_label.config(text=f"字段数: {len(self.tree.get_children())}")
            
        except Exception as e:
            self.info_label.config(text=f"更新错误: {e}")
            
    def update_field_row(self, field, value, current_time, field_defs):
        """更新单个字段行 - 防闪烁"""
        try:
            field_def = field_defs.get(field, {})
            display_name = field_def.get("display_name", field)
            category = field_def.get("category", "未知")
            is_required = field_def.get("required", False)
            
            # 格式化值
            format_func = field_def.get("format", lambda x: str(x) if x is not None else "N/A")
            formatted_value = format_func(value)
            value_type = type(value).__name__
            
            # 检查值是否改变
            old_value = self.last_values.get(field)
            is_updated = old_value != value
            
            # 确定状态和标签
            if is_required and (value is None or value == "" or value == 0):
                status = "❌ 必需字段缺失"
                tag = "missing_required"
            elif is_updated:
                status = "🔄 更新"
                tag = "updated"
            else:
                status = "✅ 正常"
                tag = "normal"
                
            # 准备行数据
            row_data = (
                len(self.field_order) + 1,
                display_name,
                field,
                formatted_value,
                value_type,
                category,
                current_time,
                status
            )
            
            # 更新或插入行
            if field in self.field_order:
                # 更新现有行
                item_id = self.field_order[field]
                self.tree.item(item_id, values=row_data, tags=(tag,))
            else:
                # 插入新行
                item_id = self.tree.insert("", tk.END, values=row_data, tags=(tag,))
                self.field_order[field] = item_id
                
            # 更新存储的值
            self.last_values[field] = value
            
        except Exception as e:
            print(f"更新字段行错误 {field}: {e}")
            
    def on_closing(self):
        """窗口关闭事件"""
        self.window.destroy()
        self.window = None

class Comma3Simulator:
    def __init__(self):
        """Initialize the Comma3 simulator"""
        print("Comma3 Device Simulator Starting...")
        
        # Network configuration
        self.broadcast_port = 7705
        self.main_port = 7706
        self.route_port = 7709
        self.zmq_port = 7710
        self.kisa_port = 12345
        
        # Server state
        self.is_running = False
        self.connected_clients = []
        self.broadcast_ip = "255.255.255.255"
        self.local_ip = self.get_local_ip()
        
        # Vehicle simulation data - 基于CarrotMan逆向分析优化
        self.vehicle_data = self.init_vehicle_data()
        self.route_points = []
        
        # 路线状态 - 基于CarrotMan实现
        self.navi_points_start_index = 0
        self.navi_points_active = False

        # CarrotMan状态机 - 基于carrot_man.py逆向分析
        self.carrot_state = self.init_carrot_state()

        # Navigation data analysis
        self.received_navigation_data = []
        self.navigation_statistics = {
            "total_messages": 0,
            "message_types": {},
            "last_update": 0,
            "data_rate": 0.0
        }
        
        # Enhanced navigation display data
        self.current_navigation_data = {}
        self.navigation_display_rows = {}  # Track treeview rows
        self.last_gui_update = 0
        self.pending_updates = False
        
        # CarrotMan命令字段专门跟踪
        self.carrot_commands = []  # 存储所有接收到的命令
        self.current_carrot_cmd = ""  # 当前命令
        self.current_carrot_arg = ""  # 当前参数
        self.current_carrot_index = 0  # 当前索引
        self.last_carrot_cmd_index = 0  # 上次命令索引
        
        # GUI components
        self.root = None
        self.realtime_window = None
        self.setup_gui()
        
        # Network threads
        self.threads = []
        
    def get_local_ip(self) -> str:
        """Get local IP address"""
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.connect(("8.8.8.8", 80))
                return s.getsockname()[0]
        except Exception:
            return "127.0.0.1"
    
    def init_vehicle_data(self) -> Dict[str, Any]:
        """Initialize vehicle simulation data - 基于CarrotMan逆向分析优化"""
        return {
            # 基础车辆状态 - 从carState获取
            "v_ego_kph": 0,                    # 当前速度 (km/h)
            "v_cruise_kph": 0,                 # 巡航速度 (km/h)
            "speed_limit": 60,                 # 道路限速 (km/h)
            "speed_limit_distance": 0,         # 限速距离 (m)
            "steering_angle_deg": 0.0,         # 转向角度 (度)
            "steering_pressed": False,         # 转向按钮状态
            "steering_torque": 0.0,            # 转向扭矩
            "gas_pressed": False,              # 油门踏板状态
            "brake_pressed": False,            # 刹车踏板状态
            "left_blinker": False,             # 左转向灯状态
            "soft_hold_active": False,         # 软保持激活状态
            "log_carrot": "",                  # 调试日志
            
            # 系统状态 - 从selfdriveState获取
            "active": False,                   # 自动驾驶激活状态
            "distance_traveled": 0,            # 行驶距离 (m)
            "is_onroad": False,                # 是否在路上
            "is_metric": True,                 # 是否使用公制单位
            
            # 模型数据 - 从modelV2获取
            "orientation_rate_z": 0.0,         # 方向变化率 (用于弯道检测)
            "velocity_x": 0.0,                 # 速度向量
            "position_x": 0.0,                 # 位置坐标X
            "position_y": 0.0,                 # 位置坐标Y
            "lane_change_state": 0,            # 车道变换状态
            "desire_state": 0,                 # 期望状态
            
            # 雷达数据 - 从radarState获取
            "lead_one_status": 0,              # 前车检测状态
            "lead_one_d_rel": 0.0,             # 前车距离 (m)
            "lead_one_v_lead": 0.0,            # 前车速度 (m/s)
            "lead_one_a_lead": 0.0,            # 前车加速度 (m/s²)
            
            # 位置和导航
            "latitude": 37.5665,               # 首尔坐标
            "longitude": 126.9780,
            "heading": 0.0,
            "road_name": "Test Road",
            "road_limit_speed": 60,
            
            # 传统车辆状态 (保持兼容性)
            "engine_rpm": 0,
            "gear_shifter": "P",
            "gear_step": 0,
            "right_blinker": False,
            "blind_spot_left": False,
            "blind_spot_right": False,
            "following_distance": 50,
            "cruise_active": False,
            "controls_active": False,
        }
    
    def init_carrot_state(self) -> Dict[str, Any]:
        """初始化CarrotMan状态机 - 基于carrot_man.py逆向分析"""
        return {
            # CarrotMan核心状态
            "active_carrot": 0,                # 0-6级状态机
            "active_count": 0,                 # 激活计数器
            "active_sdi_count": 0,             # SDI激活计数器
            "active_sdi_count_max": 200,       # SDI最大激活时间 (20秒)
            "active_kisa_count": 0,            # KISA激活计数器
            
            # SDI (Speed Detection Information) 参数
            "nSdiType": -1,                    # SDI类型
            "nSdiSpeedLimit": 0,               # 测速限速 (km/h)
            "nSdiSection": 0,                  # 区间测速标识
            "nSdiDist": 0,                     # 测速距离 (m)
            "nSdiBlockType": -1,               # 阻塞类型
            "nSdiBlockSpeed": 0,               # 阻塞速度
            "nSdiBlockDist": 0,                # 阻塞距离
            
            # TBT (Turn-by-Turn) 参数
            "nTBTDist": 0,                     # 转弯距离 (m)
            "nTBTTurnType": -1,                # 转弯类型
            "szTBTMainText": "",               # 主要指令文本
            "szNearDirName": "",               # 近处方向名
            "szFarDirName": "",                # 远处方向名
            "nTBTNextRoadWidth": 0,            # 下一道路宽度
            
            # 下一个转弯
            "nTBTDistNext": 0,                 # 下一转弯距离
            "nTBTTurnTypeNext": -1,            # 下一转弯类型
            "szTBTMainTextNext": "",           # 下一指令文本
            
            # 目的地信息
            "nGoPosDist": 0,                   # 剩余距离 (m)
            "nGoPosTime": 0,                   # 剩余时间 (s)
            "szPosRoadName": "",               # 道路名称
            "roadcate": 8,                     # 道路类别 (0-8)
            
            # SDI Plus 参数
            "nSdiPlusType": -1,                # Plus类型
            "nSdiPlusSpeedLimit": 0,           # Plus限速
            "nSdiPlusDist": 0,                 # Plus距离
            "nSdiPlusBlockType": -1,           # Plus阻塞类型
            "nSdiPlusBlockSpeed": 0,           # Plus阻塞速度
            "nSdiPlusBlockDist": 0,            # Plus阻塞距离
            
            # 目标位置
            "goalPosX": 0.0,                   # 目标经度
            "goalPosY": 0.0,                   # 目标纬度
            "szGoalName": "",                  # 目标名称
            
            # GPS位置
            "vpPosPointLatNavi": 0.0,          # 导航GPS纬度
            "vpPosPointLonNavi": 0.0,          # 导航GPS经度
            "vpPosPointLat": 0.0,              # 当前纬度
            "vpPosPointLon": 0.0,              # 当前经度
            "nPosSpeed": 0.0,                  # 速度
            "nPosAngle": 0.0,                  # 方向角
            "nPosAnglePhone": 0.0,             # 手机方向角
            
            # GPS融合参数
            "diff_angle_count": 0,             # 角度差异计数
            "last_calculate_gps_time": 0,      # 最后GPS计算时间
            "last_update_gps_time": 0,         # 最后GPS更新时间
            "last_update_gps_time_phone": 0,   # 最后手机GPS更新时间
            "last_update_gps_time_navi": 0,    # 最后导航GPS更新时间
            "bearing_offset": 0.0,             # 方向偏移
            "bearing_measured": 0.0,           # 测量方向
            "bearing": 0.0,                    # 计算方向
            "gps_valid": False,                # GPS有效性
            "gps_accuracy_phone": 0.0,         # 手机GPS精度
            "gps_accuracy_device": 0.0,        # 设备GPS精度
            
            # 计算参数
            "totalDistance": 0,                # 总距离
            "xSpdLimit": 0,                    # 速度限制
            "xSpdDist": 0,                     # 速度距离
            "xSpdType": -1,                    # 速度类型
            "xTurnInfo": -1,                   # 转弯信息
            "xDistToTurn": 0,                  # 转弯距离
            "xTurnInfoNext": -1,               # 下一转弯信息
            "xDistToTurnNext": 0,              # 下一转弯距离
            
            # 导航类型
            "navType": "invalid",              # 导航类型
            "navModifier": "",                 # 导航修饰符
            "navTypeNext": "invalid",          # 下一导航类型
            "navModifierNext": "",             # 下一导航修饰符
            
            # 命令参数
            "carrotIndex": 0,                  # 数据包序号
            "carrotCmdIndex": 0,               # 命令序号
            "carrotCmd": "",                   # 命令
            "carrotArg": "",                   # 命令参数
            "carrotCmdIndex_last": 0,          # 上次命令序号
            
            # 交通灯状态
            "traffic_light_q": [],             # 交通灯队列
            "traffic_light_count": -1,         # 交通灯计数
            "traffic_state": 0,                # 交通状态
            
            # 倒计时参数
            "left_spd_sec": 0,                 # 速度倒计时
            "left_tbt_sec": 0,                 # 转弯倒计时
            "left_sec": 100,                   # 显示倒计时
            "max_left_sec": 100,               # 最大倒计时
            "carrot_left_sec": 100,            # Carrot倒计时
            "sdi_inform": False,               # SDI信息
            
            # 自动转弯控制
            "atc_paused": False,               # ATC暂停
            "atc_activate_count": 0,           # ATC激活计数
            "gas_override_speed": 0,           # 油门覆盖速度
            "gas_pressed_state": False,        # 油门按压状态
            "source_last": "none",             # 上次速度来源
            
            # 调试信息
            "debugText": "",                   # 调试文本
            
            # 系统状态
            "xState": 0,                       # 行驶状态
            "trafficState": 0,                 # 交通灯状态
        }
    
    def setup_gui(self):
        """Setup the GUI interface"""
        self.root = tk.Tk()
        self.root.title("Comma3 Device Simulator")
        self.root.geometry("1200x800")
        
        # Create main frame
        main_frame = ttk.Frame(self.root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # Control panel
        control_frame = ttk.LabelFrame(main_frame, text="Control Panel")
        control_frame.pack(fill=tk.X, pady=(0, 10))
        
        # Start/Stop buttons
        self.start_btn = ttk.Button(control_frame, text="Start Simulator", 
                                   command=self.start_simulator)
        self.start_btn.pack(side=tk.LEFT, padx=5, pady=5)
        
        self.stop_btn = ttk.Button(control_frame, text="Stop Simulator", 
                                  command=self.stop_simulator, state=tk.DISABLED)
        self.stop_btn.pack(side=tk.LEFT, padx=5, pady=5)
        
        # Realtime data window button
        self.realtime_btn = ttk.Button(control_frame, text="📊 实时数据窗口", 
                                      command=self.open_realtime_window)
        self.realtime_btn.pack(side=tk.LEFT, padx=5, pady=5)
        
        # CarrotMan commands export button
        self.carrot_export_btn = ttk.Button(control_frame, text="🔧 导出CarrotMan命令", 
                                           command=self.export_carrot_commands)
        self.carrot_export_btn.pack(side=tk.LEFT, padx=5, pady=5)
        
        # Status label
        self.status_label = ttk.Label(control_frame, text="Status: Stopped")
        self.status_label.pack(side=tk.LEFT, padx=20, pady=5)
        
        # Create notebook for tabs
        notebook = ttk.Notebook(main_frame)
        notebook.pack(fill=tk.BOTH, expand=True)

        # Vehicle Control Tab
        self.setup_vehicle_tab(notebook)

        # Network Monitor Tab
        self.setup_network_tab(notebook)

        # Data Display Tab
        self.setup_data_tab(notebook)

        # Navigation Data Analysis Tab - NEW
        self.setup_navigation_analysis_tab(notebook)
        
    def setup_vehicle_tab(self, notebook):
        """Setup vehicle control tab"""
        vehicle_frame = ttk.Frame(notebook)
        notebook.add(vehicle_frame, text="Vehicle Control")
        
        # Speed control
        speed_frame = ttk.LabelFrame(vehicle_frame, text="Speed Control")
        speed_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Label(speed_frame, text="Speed (km/h):").pack(side=tk.LEFT, padx=5)
        self.speed_var = tk.IntVar(value=0)
        speed_scale = ttk.Scale(speed_frame, from_=0, to=120, variable=self.speed_var,
                               orient=tk.HORIZONTAL, length=200)
        speed_scale.pack(side=tk.LEFT, padx=5)
        
        self.speed_label = ttk.Label(speed_frame, text="0 km/h")
        self.speed_label.pack(side=tk.LEFT, padx=5)
        
        # Update speed display
        def update_speed(*args):
            speed = self.speed_var.get()
            self.speed_label.config(text=f"{speed} km/h")
            self.vehicle_data["v_ego_kph"] = speed
            
        self.speed_var.trace('w', update_speed)
        
        # Gear control
        gear_frame = ttk.LabelFrame(vehicle_frame, text="Gear Control")
        gear_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.gear_var = tk.StringVar(value="P")
        for gear in ["P", "R", "N", "D"]:
            ttk.Radiobutton(gear_frame, text=gear, variable=self.gear_var, 
                           value=gear, command=self.update_gear).pack(side=tk.LEFT, padx=10)
        
        # Turn signals
        signal_frame = ttk.LabelFrame(vehicle_frame, text="Turn Signals")
        signal_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.left_signal_var = tk.BooleanVar()
        self.right_signal_var = tk.BooleanVar()
        
        ttk.Checkbutton(signal_frame, text="Left Blinker", 
                       variable=self.left_signal_var, 
                       command=self.update_signals).pack(side=tk.LEFT, padx=10)
        ttk.Checkbutton(signal_frame, text="Right Blinker", 
                       variable=self.right_signal_var,
                       command=self.update_signals).pack(side=tk.LEFT, padx=10)
        
        # System status
        system_frame = ttk.LabelFrame(vehicle_frame, text="System Status")
        system_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.onroad_var = tk.BooleanVar()
        self.cruise_var = tk.BooleanVar()
        
        ttk.Checkbutton(system_frame, text="On Road", 
                       variable=self.onroad_var,
                       command=self.update_system_status).pack(side=tk.LEFT, padx=10)
        ttk.Checkbutton(system_frame, text="Cruise Active", 
                       variable=self.cruise_var,
                       command=self.update_system_status).pack(side=tk.LEFT, padx=10)
        
        # CarrotMan状态控制
        carrot_frame = ttk.LabelFrame(vehicle_frame, text="CarrotMan Control")
        carrot_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Button(carrot_frame, text="激活SDI", 
                  command=self.activate_sdi).pack(side=tk.LEFT, padx=5)
        ttk.Button(carrot_frame, text="激活KISA", 
                  command=self.activate_kisa).pack(side=tk.LEFT, padx=5)
        ttk.Button(carrot_frame, text="模拟转弯", 
                  command=self.simulate_turn).pack(side=tk.LEFT, padx=5)
        ttk.Button(carrot_frame, text="模拟限速", 
                  command=self.simulate_speed_limit).pack(side=tk.LEFT, padx=5)
    
    def setup_network_tab(self, notebook):
        """Setup network monitoring tab"""
        network_frame = ttk.Frame(notebook)
        notebook.add(network_frame, text="Network Monitor")
        
        # Connection status
        conn_frame = ttk.LabelFrame(network_frame, text="Connection Status")
        conn_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.conn_text = scrolledtext.ScrolledText(conn_frame, height=8)
        self.conn_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Message log
        msg_frame = ttk.LabelFrame(network_frame, text="Message Log")
        msg_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        self.msg_text = scrolledtext.ScrolledText(msg_frame, height=15)
        self.msg_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
    def setup_data_tab(self, notebook):
        """Setup data display tab"""
        data_frame = ttk.Frame(notebook)
        notebook.add(data_frame, text="Data Display")

        # Raw data display
        self.data_text = scrolledtext.ScrolledText(data_frame)
        self.data_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

    def setup_navigation_analysis_tab(self, notebook):
        """Setup navigation data analysis tab"""
        nav_frame = ttk.Frame(notebook)
        notebook.add(nav_frame, text="导航数据解析")

        # Create main container with paned window for resizable sections
        paned_window = ttk.PanedWindow(nav_frame, orient=tk.VERTICAL)
        paned_window.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        # Top section: Statistics and controls
        stats_frame = ttk.LabelFrame(paned_window, text="导航数据统计")
        paned_window.add(stats_frame, weight=1)

        # Statistics display
        stats_container = ttk.Frame(stats_frame)
        stats_container.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        # Create statistics grid
        self.setup_navigation_statistics(stats_container)

        # Middle section: Real-time data display
        realtime_frame = ttk.LabelFrame(paned_window, text="实时导航数据")
        paned_window.add(realtime_frame, weight=2)

        # Real-time data tree view
        self.setup_realtime_navigation_display(realtime_frame)

        # Bottom section: Message log
        log_frame = ttk.LabelFrame(paned_window, text="导航消息日志")
        paned_window.add(log_frame, weight=2)

        # Message log with filtering
        self.setup_navigation_message_log(log_frame)

    def setup_navigation_statistics(self, parent):
        """Setup navigation statistics display"""
        # Create grid layout for statistics
        stats_grid = ttk.Frame(parent)
        stats_grid.pack(fill=tk.BOTH, expand=True)

        # Row 1: Basic statistics
        ttk.Label(stats_grid, text="总消息数:").grid(row=0, column=0, sticky=tk.W, padx=5, pady=2)
        self.total_msg_label = ttk.Label(stats_grid, text="0", font=("Arial", 10, "bold"))
        self.total_msg_label.grid(row=0, column=1, sticky=tk.W, padx=5, pady=2)

        ttk.Label(stats_grid, text="数据速率:").grid(row=0, column=2, sticky=tk.W, padx=5, pady=2)
        self.data_rate_label = ttk.Label(stats_grid, text="0.0 msg/s", font=("Arial", 10, "bold"))
        self.data_rate_label.grid(row=0, column=3, sticky=tk.W, padx=5, pady=2)

        # Row 2: Message type distribution
        ttk.Label(stats_grid, text="消息类型分布:").grid(row=1, column=0, sticky=tk.W, padx=5, pady=2)
        self.msg_types_text = tk.Text(stats_grid, height=3, width=50)
        self.msg_types_text.grid(row=1, column=1, columnspan=3, sticky=tk.W+tk.E, padx=5, pady=2)

        # Row 3: Control buttons
        control_frame = ttk.Frame(stats_grid)
        control_frame.grid(row=2, column=0, columnspan=4, sticky=tk.W+tk.E, padx=5, pady=5)

        ttk.Button(control_frame, text="清空数据", command=self.clear_navigation_data).grid(row=0, column=0, padx=5)
        ttk.Button(control_frame, text="导出数据", command=self.export_navigation_data).grid(row=0, column=1, padx=5)
        ttk.Button(control_frame, text="刷新统计", command=self.refresh_navigation_stats).grid(row=0, column=2, padx=5)

    def setup_realtime_navigation_display(self, parent):
        """Setup optimized real-time navigation data display"""
        # Create main container
        display_frame = ttk.Frame(parent)
        display_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        # Create control frame for pause and export buttons
        control_frame = ttk.Frame(display_frame)
        control_frame.pack(fill=tk.X, pady=(0, 5))
        
        # Pause button
        self.pause_var = tk.BooleanVar()
        self.pause_btn = ttk.Button(control_frame, text="⏸️ 暂停更新", 
                                   command=self.toggle_pause)
        self.pause_btn.pack(side=tk.LEFT, padx=5)
        
        # Export button
        self.export_btn = ttk.Button(control_frame, text="📁 导出数据", 
                                    command=self.export_navigation_data)
        self.export_btn.pack(side=tk.LEFT, padx=5)
        
        # Clear data button
        self.clear_btn = ttk.Button(control_frame, text="🗑️ 清空数据", 
                                   command=self.clear_navigation_data)
        self.clear_btn.pack(side=tk.LEFT, padx=5)
        
        # Status label
        self.status_label = ttk.Label(control_frame, text="状态: 运行中")
        self.status_label.pack(side=tk.RIGHT, padx=5)

        # Create treeview for table display with original field names
        columns = ("字段名称", "原始字段名", "当前值", "数据类型", "分类", "更新时间", "状态")
        self.nav_tree = ttk.Treeview(display_frame, columns=columns, show="headings", height=20)

        # Configure columns with better widths and smaller font
        column_config = {
            "字段名称": 120,
            "原始字段名": 120,
            "当前值": 150, 
            "数据类型": 60,
            "分类": 80,
            "更新时间": 100,
            "状态": 60
        }

        for col in columns:
            self.nav_tree.heading(col, text=col, anchor=tk.W)
            self.nav_tree.column(col, width=column_config.get(col, 80), anchor=tk.W)

        # Add scrollbars
        v_scrollbar = ttk.Scrollbar(display_frame, orient=tk.VERTICAL, command=self.nav_tree.yview)
        h_scrollbar = ttk.Scrollbar(display_frame, orient=tk.HORIZONTAL, command=self.nav_tree.xview)
        self.nav_tree.configure(yscrollcommand=v_scrollbar.set, xscrollcommand=h_scrollbar.set)

        # Pack elements using pack manager
        self.nav_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        v_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        h_scrollbar.pack(side=tk.BOTTOM, fill=tk.X)

        # Configure row colors for different states
        self.nav_tree.tag_configure("new", background="#e8f5e8")      # Light green for new data
        self.nav_tree.tag_configure("updated", background="#fff2cc")   # Light yellow for updated
        self.nav_tree.tag_configure("normal", background="white")      # White for unchanged
        self.nav_tree.tag_configure("missing_required", background="#ffebee", foreground="#d32f2f")  # Red for missing required fields
        self.nav_tree.tag_configure("missing_important", background="#fff3e0", foreground="#f57c00")  # Orange for missing important fields
        
        # Initialize pause state
        self.is_paused = False

    def setup_navigation_message_log(self, parent):
        """Setup navigation message log with filtering"""
        log_container = ttk.Frame(parent)
        log_container.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        # Filter controls
        filter_frame = ttk.Frame(log_container)
        filter_frame.pack(fill=tk.X, pady=(0, 5))

        ttk.Label(filter_frame, text="过滤:").pack(side=tk.LEFT, padx=5)
        self.filter_var = tk.StringVar()
        filter_entry = ttk.Entry(filter_frame, textvariable=self.filter_var, width=20)
        filter_entry.pack(side=tk.LEFT, padx=5)
        filter_entry.bind('<KeyRelease>', self.filter_navigation_log)

        ttk.Button(filter_frame, text="清空日志", command=self.clear_navigation_log).pack(side=tk.LEFT, padx=5)

        # Auto-scroll checkbox
        self.auto_scroll_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(filter_frame, text="自动滚动", variable=self.auto_scroll_var).pack(side=tk.LEFT, padx=5)

        # Message log text area
        self.nav_log_text = scrolledtext.ScrolledText(log_container, height=12, wrap=tk.WORD)
        self.nav_log_text.pack(fill=tk.BOTH, expand=True)

    def clear_navigation_data(self):
        """Clear all navigation data"""
        self.received_navigation_data.clear()
        self.navigation_statistics = {
            "total_messages": 0,
            "message_types": {},
            "last_update": 0,
            "data_rate": 0.0
        }
        self.refresh_navigation_display()
        self.log_navigation_message("📝 导航数据已清空")

    def toggle_pause(self):
        """Toggle pause state for navigation display"""
        self.is_paused = not self.is_paused
        if self.is_paused:
            self.pause_btn.config(text="▶️ 继续更新")
            self.status_label.config(text="状态: 已暂停")
            self.log_navigation_message("⏸️ 显示已暂停，可以检查数据字段")
        else:
            self.pause_btn.config(text="⏸️ 暂停更新")
            self.status_label.config(text="状态: 运行中")
            self.log_navigation_message("▶️ 显示已恢复")
    
    def export_navigation_data(self):
        """Export navigation data to multiple formats"""
        try:
            from tkinter import filedialog
            import csv

            # Ask user to select export format
            export_format = filedialog.asksaveasfilename(
                defaultextension=".txt",
                filetypes=[
                    ("Text files", "*.txt"),
                    ("JSON files", "*.json"), 
                    ("CSV files", "*.csv"),
                    ("All files", "*.*")
                ],
                title="导出导航数据"
            )

            if export_format:
                if export_format.endswith('.json'):
                    self.export_to_json(export_format)
                elif export_format.endswith('.csv'):
                    self.export_to_csv(export_format)
                else:
                    self.export_to_text(export_format)

        except Exception as e:
            self.log_navigation_message(f"❌ 导出失败: {e}")
    
    def export_to_text(self, filename):
        """Export data to text file"""
        try:
            with open(filename, 'w', encoding='utf-8') as f:
                f.write("=== Comma3 模拟器导航数据导出 ===\n")
                f.write(f"导出时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write(f"总消息数: {self.navigation_statistics['total_messages']}\n")
                f.write(f"数据速率: {self.navigation_statistics['data_rate']:.1f} msg/s\n\n")
                
                f.write("=== 消息类型分布 ===\n")
                for msg_type, count in self.navigation_statistics["message_types"].items():
                    f.write(f"{msg_type}: {count}\n")
                f.write("\n")
                
                f.write("=== 接收到的原始数据 ===\n")
                for i, entry in enumerate(self.received_navigation_data):
                    f.write(f"\n--- 消息 {i+1} ---\n")
                    f.write(f"时间戳: {entry['timestamp']}\n")
                    f.write(f"来源: {entry['source_ip']}:{entry['source_port']}\n")
                    f.write(f"数据大小: {entry['data_size']} 字节\n")
                    f.write(f"数据内容: {entry['data']}\n")
                
                f.write("\n=== 当前导航数据 ===\n")
                for field, value in self.current_navigation_data.items():
                    f.write(f"{field}: {value}\n")
            
            self.log_navigation_message(f"📁 文本数据已导出到: {filename}")
            
        except Exception as e:
            self.log_navigation_message(f"❌ 文本导出失败: {e}")
    
    def export_to_json(self, filename):
        """Export data to JSON file"""
        try:
            import json
            
            export_data = {
                "export_info": {
                    "export_time": datetime.now().isoformat(),
                    "total_messages": self.navigation_statistics['total_messages'],
                    "data_rate": self.navigation_statistics['data_rate']
                },
                "statistics": self.navigation_statistics,
                "navigation_data": self.received_navigation_data,
                "current_data": self.current_navigation_data
            }

            with open(filename, 'w', encoding='utf-8') as f:
                json.dump(export_data, f, indent=2, ensure_ascii=False)

            self.log_navigation_message(f"📁 JSON数据已导出到: {filename}")
            
        except Exception as e:
            self.log_navigation_message(f"❌ JSON导出失败: {e}")
    
    def export_to_csv(self, filename):
        """Export data to CSV file"""
        try:
            import csv
            
            with open(filename, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                
                # Write header
                writer.writerow(['时间戳', '来源IP', '来源端口', '数据大小', '消息类型', '数据内容'])
                
                # Write data rows
                for entry in self.received_navigation_data:
                    msg_type = self.analyze_message_type(entry['data'])
                    writer.writerow([
                        entry['timestamp'],
                        entry['source_ip'],
                        entry['source_port'],
                        entry['data_size'],
                        msg_type,
                        str(entry['data'])
                    ])
            
            self.log_navigation_message(f"📁 CSV数据已导出到: {filename}")
            
        except Exception as e:
            self.log_navigation_message(f"❌ CSV导出失败: {e}")

    def refresh_navigation_stats(self):
        """Refresh navigation statistics display"""
        self.update_navigation_statistics()
        self.refresh_navigation_display()

    def filter_navigation_log(self, event=None):
        """Filter navigation log based on search term"""
        # This would implement log filtering functionality
        # For now, just log the filter action
        filter_text = self.filter_var.get()
        if filter_text:
            self.log_navigation_message(f"🔍 应用过滤器: {filter_text}")
        # Note: event parameter is used by tkinter binding

    def clear_navigation_log(self):
        """Clear navigation message log"""
        if hasattr(self, 'nav_log_text'):
            self.nav_log_text.delete(1.0, tk.END)

    def log_navigation_message(self, message: str):
        """Log message to navigation log"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        formatted_msg = f"[{timestamp}] {message}\n"

        if hasattr(self, 'nav_log_text'):
            self.nav_log_text.insert(tk.END, formatted_msg)
            if self.auto_scroll_var.get():
                self.nav_log_text.see(tk.END)

    def update_navigation_statistics(self):
        """Update navigation statistics"""
        current_time = time.time()

        # Calculate data rate
        if self.navigation_statistics["last_update"] > 0:
            time_diff = current_time - self.navigation_statistics["last_update"]
            if time_diff > 0:
                recent_messages = len([msg for msg in self.received_navigation_data
                                     if msg.get("timestamp", 0) > current_time - 10])
                self.navigation_statistics["data_rate"] = recent_messages / min(10, time_diff)

        self.navigation_statistics["last_update"] = current_time

        # Update GUI elements
        if hasattr(self, 'total_msg_label'):
            self.total_msg_label.config(text=str(self.navigation_statistics["total_messages"]))

        if hasattr(self, 'data_rate_label'):
            rate = self.navigation_statistics["data_rate"]
            self.data_rate_label.config(text=f"{rate:.1f} msg/s")

        if hasattr(self, 'msg_types_text'):
            self.msg_types_text.delete(1.0, tk.END)
            for msg_type, count in self.navigation_statistics["message_types"].items():
                self.msg_types_text.insert(tk.END, f"{msg_type}: {count}\n")

    def refresh_navigation_display(self):
        """Refresh navigation display with optimized incremental updates and field validation"""
        if not hasattr(self, 'nav_tree') or not self.current_navigation_data:
            return
            
        # Check if paused
        if self.is_paused:
            return

        try:
            # Get current time for timestamps
            current_time = datetime.now().strftime("%H:%M:%S")
            
            # Process each field in the current navigation data
            for field, value in self.current_navigation_data.items():
                self.update_navigation_row(field, value, current_time)

            # Clean up rows for fields no longer present
            self.cleanup_obsolete_rows()
            
            # Log field statistics periodically (every 10 updates)
            if not hasattr(self, '_display_update_count'):
                self._display_update_count = 0
            self._display_update_count += 1
            
            if self._display_update_count % 10 == 0:
                self.log_field_statistics()

        except Exception as e:
            self.log_navigation_message(f"❌ 显示更新错误: {e}")

    def update_navigation_row(self, field: str, value: Any, current_time: str):
        """Update or create a single navigation row with field validation and highlighting"""
        try:
            # Get field definition
            field_def = self.get_navigation_field_definitions().get(field, {})
            display_name = field_def.get("display_name", field)
            category = field_def.get("category", "未知")
            is_required = field_def.get("required", False)
            highlight_missing = field_def.get("highlight_missing", False)
            description = field_def.get("description", "")
            
            # Format value
            format_func = field_def.get("format", lambda x: str(x) if x is not None else "N/A")
            formatted_value = format_func(value)
            value_type = type(value).__name__
            
            # Determine if this is new or updated data
            row_id = f"nav_{field}"
            is_new = row_id not in self.navigation_display_rows
            
            # Check if value changed
            old_value = self.navigation_display_rows.get(row_id, {}).get('value')
            is_updated = not is_new and old_value != value
            
            # Determine status and tag based on field validation
            if is_required and (value is None or value == "" or value == 0):
                status = "❌ 必需字段缺失"
                tag = "missing_required"
            elif highlight_missing and (value is None or value == "" or value == 0):
                status = "⚠️ 重要字段缺失"
                tag = "missing_important"
            elif is_new:
                status = "✅ 新增"
                tag = "new"
            elif is_updated:
                status = "🔄 更新"
                tag = "updated"
            else:
                status = "✅ 正常"
                tag = "normal"

            # Prepare row data with original field name
            row_data = (display_name, field, formatted_value, value_type, category, current_time, status)

            if is_new:
                # Insert new row in correct position (sorted by priority)
                priority = field_def.get("priority", 999)
                insert_position = self.find_insert_position(priority)
                
                item_id = self.nav_tree.insert("", insert_position, iid=row_id, 
                                             values=row_data, tags=(tag,))
                
                # Store row information
                self.navigation_display_rows[row_id] = {
                    'value': value,
                    'item_id': item_id,
                    'field': field,
                    'priority': priority,
                    'is_required': is_required,
                    'description': description
                }
                
            else:
                # Update existing row only if value changed to prevent flickering
                if self.navigation_display_rows[row_id]['value'] != value:
                    self.nav_tree.item(row_id, values=row_data, tags=(tag,))
                    self.navigation_display_rows[row_id]['value'] = value

        except Exception as e:
            self.log_navigation_message(f"❌ 行更新错误 {field}: {e}")

    def find_insert_position(self, priority: int) -> int:
        """Find correct insert position based on priority"""
        try:
            children = self.nav_tree.get_children()
            for i, child_id in enumerate(children):
                if child_id in self.navigation_display_rows:
                    child_priority = self.navigation_display_rows[child_id].get('priority', 999)
                    if priority < child_priority:
                        return i
            return len(children)
        except Exception:
            return 0

    def cleanup_obsolete_rows(self):
        """Remove rows for fields no longer present in current data"""
        try:
            current_fields = set(f"nav_{field}" for field in self.current_navigation_data.keys())
            display_rows = set(self.navigation_display_rows.keys())
            
            obsolete_rows = display_rows - current_fields
            for row_id in obsolete_rows:
                try:
                    self.nav_tree.delete(row_id)
                    del self.navigation_display_rows[row_id]
                except Exception:
                    pass  # Row might already be deleted
                    
        except Exception as e:
            self.log_navigation_message(f"❌ 清理过期行错误: {e}")

    def get_field_statistics(self) -> Dict[str, Any]:
        """获取字段统计信息，用于显示缺失和错误字段"""
        try:
            field_defs = self.get_navigation_field_definitions()
            stats = {
                "total_fields": len(field_defs),
                "present_fields": 0,
                "missing_required": 0,
                "missing_important": 0,
                "missing_fields": [],
                "required_fields": [],
                "important_fields": []
            }
            
            for field, field_def in field_defs.items():
                value = self.current_navigation_data.get(field)
                is_required = field_def.get("required", False)
                highlight_missing = field_def.get("highlight_missing", False)
                
                if value is not None and value != "" and value != 0:
                    stats["present_fields"] += 1
                else:
                    stats["missing_fields"].append(field)
                    if is_required:
                        stats["missing_required"] += 1
                        stats["required_fields"].append(field)
                    elif highlight_missing:
                        stats["missing_important"] += 1
                        stats["important_fields"].append(field)
            
            return stats
            
        except Exception as e:
            self.log_navigation_message(f"❌ 字段统计错误: {e}")
            return {}

    def log_field_statistics(self):
        """记录字段统计信息到日志"""
        try:
            stats = self.get_field_statistics()
            if stats:
                self.log_navigation_message(
                    f"📊 字段统计: 总计{stats['total_fields']}个字段, "
                    f"已显示{stats['present_fields']}个, "
                    f"缺失{len(stats['missing_fields'])}个"
                )
                
                if stats["missing_required"] > 0:
                    self.log_navigation_message(
                        f"❌ 必需字段缺失({stats['missing_required']}个): "
                        f"{', '.join(stats['required_fields'][:5])}"
                        + ("..." if len(stats['required_fields']) > 5 else "")
                    )
                
                if stats["missing_important"] > 0:
                    self.log_navigation_message(
                        f"⚠️ 重要字段缺失({stats['missing_important']}个): "
                        f"{', '.join(stats['important_fields'][:5])}"
                        + ("..." if len(stats['important_fields']) > 5 else "")
                    )
                    
        except Exception as e:
            self.log_navigation_message(f"❌ 字段统计日志错误: {e}")

    def analyze_message_type(self, data: Dict[str, Any]) -> str:
        """Analyze and categorize the message type based on data content"""
        if "nTBTDist" in data or "nTBTTurnType" in data:
            return "转弯引导"
        elif "nSdiType" in data or "nSdiDist" in data:
            return "摄像头信息"
        elif "vpPosPointLat" in data and "vpPosPointLon" in data:
            return "位置信息"
        elif "nRoadLimitSpeed" in data:
            return "限速信息"
        elif "traffic_state" in data:
            return "交通状态"
        elif "szPosRoadName" in data:
            return "道路信息"
        elif "active_carrot" in data:
            return "CarrotMan状态"
        else:
            return "其他数据"

    def get_turn_type_description(self, turn_type: int) -> str:
        """Get description for turn type"""
        turn_types = {
            -1: "无转弯",
            0: "直行",
            1: "右转",
            2: "左转",
            3: "掉头",
            4: "右前方",
            5: "左前方",
            6: "右后方",
            7: "左后方",
            8: "进入环岛",
            9: "离开环岛",
            10: "进入高速",
            11: "离开高速",
            12: "进入隧道",
            13: "离开隧道"
        }
        return turn_types.get(turn_type, f"未知转弯类型({turn_type})")

    def get_sdi_type_description(self, sdi_type: int) -> str:
        """Get description for SDI camera type"""
        sdi_types = {
            -1: "无摄像头",
            1: "固定测速",
            2: "区间测速开始",
            3: "区间测速结束",
            7: "违章摄像头",
            8: "红绿灯摄像头",
            22: "移动测速"
        }
        return sdi_types.get(sdi_type, f"未知摄像头类型({sdi_type})")

    def get_traffic_state_description(self, traffic_state: int) -> str:
        """Get description for traffic state"""
        traffic_states = {
            0: "无信号",
            1: "红灯",
            2: "绿灯",
            3: "左转信号"
        }
        return traffic_states.get(traffic_state, f"未知交通状态({traffic_state})")

    def get_active_carrot_description(self, active_carrot: int) -> str:
        """获取active_carrot状态描述"""
        descriptions = {
            0: "未激活",
            1: "CarrotMan激活",
            2: "SDI激活",
            3: "速度减速激活",
            4: "区间激活",
            5: "减速带激活",
            6: "速度限制激活"
        }
        return descriptions.get(active_carrot, f"未知状态({active_carrot})")

    def get_navigation_field_definitions(self) -> Dict[str, Dict[str, Any]]:
        """获取导航字段定义 - 基于carrot_man.py的字段名称和结构"""
        return {
            # ===== UDP广播消息字段 (make_send_message) =====
            "Carrot2": {
                "display_name": "Carrot版本",
                "category": "UDP广播",
                "description": "Carrot系统版本号，必需字段",
                "required": True,
                "format": lambda x: str(x) if x else "❌ 缺失",
                "priority": 1,
                "highlight_missing": True
            },
            "IsOnroad": {
                "display_name": "车辆在线状态",
                "category": "UDP广播", 
                "description": "车辆是否在道路上运行，必需字段",
                "required": True,
                "format": lambda x: "✅ 在线" if x else "❌ 离线",
                "priority": 2,
                "highlight_missing": True
            },
            "CarrotRouteActive": {
                "display_name": "导航激活状态",
                "category": "UDP广播",
                "description": "导航路线是否激活",
                "required": False,
                "format": lambda x: "✅ 激活" if x else "❌ 未激活",
                "priority": 3
            },
            "ip": {
                "display_name": "IP地址",
                "category": "UDP广播",
                "description": "comma3设备IP地址",
                "required": True,
                "format": lambda x: str(x) if x else "❌ 缺失",
                "priority": 4,
                "highlight_missing": True
            },
            "port": {
                "display_name": "端口号",
                "category": "UDP广播",
                "description": "comma3设备端口号",
                "required": True,
                "format": lambda x: str(x) if x else "❌ 缺失",
                "priority": 5,
                "highlight_missing": True
            },
            "log_carrot": {
                "display_name": "Carrot日志",
                "category": "UDP广播",
                "description": "Carrot系统日志信息",
                "required": False,
                "format": lambda x: str(x) if x else "无日志",
                "priority": 6
            },
            "v_cruise_kph": {
                "display_name": "巡航速度",
                "category": "UDP广播",
                "description": "设定的巡航速度(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 7
            },
            "v_ego_kph": {
                "display_name": "当前车速",
                "category": "UDP广播",
                "description": "当前车辆速度(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 8
            },
            "tbt_dist": {
                "display_name": "转弯距离",
                "category": "UDP广播",
                "description": "到下一个转弯的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 9
            },
            "sdi_dist": {
                "display_name": "摄像头距离",
                "category": "UDP广播",
                "description": "到速度摄像头的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 10
            },
            "active": {
                "display_name": "控制激活",
                "category": "UDP广播",
                "description": "OpenPilot控制是否激活",
                "required": False,
                "format": lambda x: "✅ 激活" if x else "❌ 未激活",
                "priority": 11
            },
            "xState": {
                "display_name": "X状态",
                "category": "UDP广播",
                "description": "OpenPilot X状态",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 12
            },
            "trafficState": {
                "display_name": "交通信号状态",
                "category": "UDP广播",
                "description": "交通信号灯状态",
                "required": False,
                "format": lambda x: self.get_traffic_state_description(x) if x is not None else "N/A",
                "priority": 13
            },

            # ===== 手机应用发送给Comma3的字段 =====
            "carrotIndex": {
                "display_name": "Carrot索引",
                "category": "手机→Comma3",
                "description": "Carrot命令索引，必需字段",
                "required": True,
                "format": lambda x: str(x) if x is not None else "❌ 缺失",
                "priority": 14,
                "highlight_missing": True
            },
            "carrotCmd": {
                "display_name": "Carrot命令",
                "category": "手机→Comma3",
                "description": "Carrot命令类型",
                "required": False,
                "format": lambda x: str(x) if x else "无命令",
                "priority": 15
            },
            "carrotArg": {
                "display_name": "Carrot参数",
                "category": "手机→Comma3",
                "description": "Carrot命令参数",
                "required": False,
                "format": lambda x: str(x) if x else "无参数",
                "priority": 16
            },
            "nRoadLimitSpeed": {
                "display_name": "道路限速",
                "category": "手机→Comma3",
                "description": "道路限速(km/h)，编码格式",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 17
            },
            "nSdiType": {
                "display_name": "SDI类型",
                "category": "手机→Comma3",
                "description": "速度检测信息类型",
                "required": False,
                "format": lambda x: self.get_sdi_type_description(x) if x is not None else "N/A",
                "priority": 18
            },
            "nSdiSpeedLimit": {
                "display_name": "SDI限速",
                "category": "手机→Comma3",
                "description": "SDI速度限制(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 19
            },
            "nSdiDist": {
                "display_name": "SDI距离",
                "category": "手机→Comma3",
                "description": "到SDI点的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 20
            },
            "nTBTTurnType": {
                "display_name": "TBT转弯类型",
                "category": "手机→Comma3",
                "description": "转弯类型代码",
                "required": False,
                "format": lambda x: self.get_turn_type_description(x) if x is not None else "N/A",
                "priority": 21
            },
            "nTBTDist": {
                "display_name": "TBT距离",
                "category": "手机→Comma3",
                "description": "到转弯点的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 22
            },
            "szTBTMainText": {
                "display_name": "TBT主文本",
                "category": "手机→Comma3",
                "description": "转弯指令文本",
                "required": False,
                "format": lambda x: str(x) if x else "无指令",
                "priority": 23
            },
            "vpPosPointLat": {
                "display_name": "GPS纬度",
                "category": "手机→Comma3",
                "description": "GPS纬度坐标",
                "required": False,
                "format": lambda x: f"{x:.6f}°" if x else "N/A",
                "priority": 24
            },
            "vpPosPointLon": {
                "display_name": "GPS经度",
                "category": "手机→Comma3",
                "description": "GPS经度坐标",
                "required": False,
                "format": lambda x: f"{x:.6f}°" if x else "N/A",
                "priority": 25
            },
            "nPosAngle": {
                "display_name": "GPS方向角",
                "category": "手机→Comma3",
                "description": "GPS方向角度",
                "required": False,
                "format": lambda x: f"{x}°" if x is not None else "N/A",
                "priority": 26
            },
            "nPosSpeed": {
                "display_name": "GPS速度",
                "category": "手机→Comma3",
                "description": "GPS速度(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 27
            },

            # ===== Comma3内部计算字段 (carrotMan消息) =====
            "activeCarrot": {
                "display_name": "CarrotMan激活状态",
                "category": "内部计算",
                "description": "CarrotMan状态机(0-6)，关键字段",
                "required": True,
                "format": lambda x: self.get_active_carrot_description(x) if x is not None else "❌ 缺失",
                "priority": 28,
                "highlight_missing": True
            },
            "nRoadLimitSpeed": {
                "display_name": "道路限速(解码)",
                "category": "内部计算",
                "description": "解码后的道路限速",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 29
            },
            "xSpdType": {
                "display_name": "速度类型",
                "category": "内部计算",
                "description": "当前速度限制类型",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 30
            },
            "xSpdLimit": {
                "display_name": "速度限制",
                "category": "内部计算",
                "description": "当前速度限制(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 31
            },
            "xSpdDist": {
                "display_name": "速度距离",
                "category": "内部计算",
                "description": "到速度限制点的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 32
            },
            "xSpdCountDown": {
                "display_name": "速度倒计时",
                "category": "内部计算",
                "description": "速度限制倒计时(秒)",
                "required": False,
                "format": lambda x: f"{x}秒" if x is not None else "N/A",
                "priority": 33
            },
            "xTurnInfo": {
                "display_name": "转弯信息",
                "category": "内部计算",
                "description": "转弯信息代码",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 34
            },
            "xDistToTurn": {
                "display_name": "转弯距离",
                "category": "内部计算",
                "description": "到转弯点的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 35
            },
            "xTurnCountDown": {
                "display_name": "转弯倒计时",
                "category": "内部计算",
                "description": "转弯倒计时(秒)",
                "required": False,
                "format": lambda x: f"{x}秒" if x is not None else "N/A",
                "priority": 36
            },
            "atcType": {
                "display_name": "ATC类型",
                "category": "内部计算",
                "description": "自适应巡航控制类型",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 37
            },
            "vTurnSpeed": {
                "display_name": "转弯速度",
                "category": "内部计算",
                "description": "建议转弯速度(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 38
            },
            "szPosRoadName": {
                "display_name": "位置道路名称",
                "category": "内部计算",
                "description": "当前位置道路名称",
                "required": False,
                "format": lambda x: str(x) if x else "未知道路",
                "priority": 39
            },
            "szTBTMainText": {
                "display_name": "TBT主文本",
                "category": "内部计算",
                "description": "转弯指令文本",
                "required": False,
                "format": lambda x: str(x) if x else "无指令",
                "priority": 40
            },
            "desiredSpeed": {
                "display_name": "期望速度",
                "category": "内部计算",
                "description": "期望行驶速度(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 41
            },
            "desiredSource": {
                "display_name": "速度来源",
                "category": "内部计算",
                "description": "期望速度的来源",
                "required": False,
                "format": lambda x: str(x) if x else "N/A",
                "priority": 42
            },
            "carrotCmdIndex": {
                "display_name": "命令索引",
                "category": "内部计算",
                "description": "Carrot命令索引",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 43
            },
            "carrotCmd": {
                "display_name": "Carrot命令",
                "category": "内部计算",
                "description": "Carrot命令",
                "required": False,
                "format": lambda x: str(x) if x else "N/A",
                "priority": 44
            },
            "carrotArg": {
                "display_name": "Carrot参数",
                "category": "内部计算",
                "description": "Carrot命令参数",
                "required": False,
                "format": lambda x: str(x) if x else "N/A",
                "priority": 45
            },
            "trafficState": {
                "display_name": "交通状态",
                "category": "内部计算",
                "description": "交通信号状态",
                "required": False,
                "format": lambda x: self.get_traffic_state_description(x) if x is not None else "N/A",
                "priority": 46
            },
            "xPosSpeed": {
                "display_name": "位置速度",
                "category": "内部计算",
                "description": "位置速度(km/h)",
                "required": False,
                "format": lambda x: f"{x} km/h" if x is not None else "N/A",
                "priority": 47
            },
            "xPosAngle": {
                "display_name": "位置角度",
                "category": "内部计算",
                "description": "位置角度",
                "required": False,
                "format": lambda x: f"{x}°" if x is not None else "N/A",
                "priority": 48
            },
            "xPosLat": {
                "display_name": "位置纬度",
                "category": "内部计算",
                "description": "位置纬度",
                "required": False,
                "format": lambda x: f"{x:.6f}°" if x else "N/A",
                "priority": 49
            },
            "xPosLon": {
                "display_name": "位置经度",
                "category": "内部计算",
                "description": "位置经度",
                "required": False,
                "format": lambda x: f"{x:.6f}°" if x else "N/A",
                "priority": 50
            },
            "nGoPosDist": {
                "display_name": "目的地距离",
                "category": "内部计算",
                "description": "到目的地的距离(m)",
                "required": False,
                "format": lambda x: f"{x}m" if x is not None else "N/A",
                "priority": 51
            },
            "nGoPosTime": {
                "display_name": "目的地时间",
                "category": "内部计算",
                "description": "到目的地的预计时间(分钟)",
                "required": False,
                "format": lambda x: f"{x}分钟" if x is not None else "N/A",
                "priority": 52
            },
            "szSdiDescr": {
                "display_name": "SDI描述",
                "category": "内部计算",
                "description": "SDI描述文本",
                "required": False,
                "format": lambda x: str(x) if x else "N/A",
                "priority": 53
            },
            "leftSec": {
                "display_name": "剩余秒数",
                "category": "内部计算",
                "description": "剩余时间(秒)",
                "required": False,
                "format": lambda x: f"{x}秒" if x is not None else "N/A",
                "priority": 54
            },

            # ===== CarrotMan内部状态字段 =====
            "carrot_active_count": {
                "display_name": "激活计数器",
                "category": "CarrotMan状态",
                "description": "CarrotMan激活计数器",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 55
            },
            "carrot_active_sdi_count": {
                "display_name": "SDI激活计数",
                "category": "CarrotMan状态",
                "description": "SDI激活计数器",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 56
            },
            "carrot_active_kisa_count": {
                "display_name": "KISA激活计数",
                "category": "CarrotMan状态",
                "description": "KISA激活计数器",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 57
            },
            "carrot_left_spd_sec": {
                "display_name": "速度剩余秒数",
                "category": "CarrotMan状态",
                "description": "速度限制剩余秒数",
                "required": False,
                "format": lambda x: f"{x}秒" if x is not None else "N/A",
                "priority": 58
            },
            "carrot_left_tbt_sec": {
                "display_name": "转弯剩余秒数",
                "category": "CarrotMan状态",
                "description": "转弯剩余秒数",
                "required": False,
                "format": lambda x: f"{x}秒" if x is not None else "N/A",
                "priority": 59
            },
            "carrot_left_sec": {
                "display_name": "总剩余秒数",
                "category": "CarrotMan状态",
                "description": "总剩余时间(秒)",
                "required": False,
                "format": lambda x: f"{x}秒" if x is not None else "N/A",
                "priority": 60
            },
            "carrot_traffic_light_count": {
                "display_name": "交通灯计数",
                "category": "CarrotMan状态",
                "description": "交通灯检测计数",
                "required": False,
                "format": lambda x: str(x) if x is not None else "N/A",
                "priority": 61
            }
        }

    def format_navigation_value(self, field: str, value: Any) -> str:
        """Format navigation value for display"""
        field_defs = self.get_navigation_field_definitions()
        if field in field_defs:
            formatter = field_defs[field]["format"]
            try:
                return formatter(value)
            except Exception:
                return str(value) if value is not None else "N/A"
        else:
            return str(value) if value is not None else "N/A"

    def get_field_display_name(self, field: str) -> str:
        """Get display name for field"""
        field_defs = self.get_navigation_field_definitions()
        return field_defs.get(field, {}).get("display_name", field)

    def get_field_category(self, field: str) -> str:
        """Get category for field"""
        field_defs = self.get_navigation_field_definitions()
        return field_defs.get(field, {}).get("category", "其他数据")
        
    def get_field_priority(self, field: str) -> int:
        """Get priority for field (lower number = higher priority)"""
        field_defs = self.get_navigation_field_definitions()
        return field_defs.get(field, {}).get("priority", 999)
        
    def update_gear(self):
        """Update gear selection"""
        gear = self.gear_var.get()
        self.vehicle_data["gear_shifter"] = gear
        if gear == "D":
            self.vehicle_data["gear_step"] = random.randint(1, 6)
        else:
            self.vehicle_data["gear_step"] = 0
            
    def update_signals(self):
        """Update turn signals"""
        self.vehicle_data["left_blinker"] = self.left_signal_var.get()
        self.vehicle_data["right_blinker"] = self.right_signal_var.get()
        
    def update_system_status(self):
        """Update system status"""
        self.vehicle_data["is_onroad"] = self.onroad_var.get()
        self.vehicle_data["cruise_active"] = self.cruise_var.get()
        self.vehicle_data["controls_active"] = self.cruise_var.get()
    
    def open_realtime_window(self):
        """打开实时数据窗口"""
        if self.realtime_window is None:
            self.realtime_window = RealtimeDataWindow(self)
        self.realtime_window.create_window()
    
    def export_carrot_commands(self):
        """导出CarrotMan命令数据"""
        try:
            from tkinter import filedialog
            
            filename = filedialog.asksaveasfilename(
                defaultextension=".txt",
                filetypes=[
                    ("Text files", "*.txt"),
                    ("JSON files", "*.json"),
                    ("CSV files", "*.csv"),
                    ("All files", "*.*")
                ],
                title="导出CarrotMan命令数据"
            )
            
            if filename:
                if filename.endswith('.json'):
                    self.export_carrot_commands_json(filename)
                elif filename.endswith('.csv'):
                    self.export_carrot_commands_csv(filename)
                else:
                    self.export_carrot_commands_text(filename)
                    
                self.log_message(f"CarrotMan命令数据已导出: {filename}")
                
        except Exception as e:
            self.log_message(f"导出CarrotMan命令失败: {e}", "ERROR")
    
    def export_carrot_commands_text(self, filename):
        """导出CarrotMan命令为文本格式"""
        try:
            with open(filename, 'w', encoding='utf-8') as f:
                f.write("=== CarrotMan 命令数据导出 ===\n")
                f.write(f"导出时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write(f"命令总数: {len(self.carrot_commands)}\n")
                f.write(f"当前命令索引: {self.current_carrot_index}\n")
                f.write(f"当前命令: {self.current_carrot_cmd}\n")
                f.write(f"当前参数: {self.current_carrot_arg}\n\n")
                
                f.write("=== 命令历史记录 ===\n")
                for i, cmd in enumerate(self.carrot_commands, 1):
                    f.write(f"\n--- 命令 #{i} ---\n")
                    f.write(f"时间: {cmd['time_str']}\n")
                    f.write(f"索引: {cmd['carrotIndex']}\n")
                    f.write(f"命令: {cmd['carrotCmd']}\n")
                    f.write(f"参数: {cmd['carrotArg']}\n")
                    f.write(f"来源IP: {cmd['source_ip']}\n")
                    
                    # 写入原始数据（简化版）
                    raw_data = cmd['raw_data']
                    f.write("原始数据字段:\n")
                    for key, value in raw_data.items():
                        if key not in ['carrotIndex', 'carrotCmd', 'carrotArg']:
                            f.write(f"  {key}: {value}\n")
                
                f.write("\n=== 当前状态 ===\n")
                f.write(f"当前CarrotMan状态:\n")
                f.write(f"  active_carrot: {self.carrot_state.get('active_carrot', 0)}\n")
                f.write(f"  active_count: {self.carrot_state.get('active_count', 0)}\n")
                f.write(f"  traffic_state: {self.carrot_state.get('traffic_state', 0)}\n")
                f.write(f"  xState: {self.carrot_state.get('xState', 0)}\n")
                
        except Exception as e:
            raise Exception(f"文本导出失败: {e}")
    
    def export_carrot_commands_json(self, filename):
        """导出CarrotMan命令为JSON格式"""
        try:
            import json
            
            export_data = {
                "export_info": {
                    "export_time": datetime.now().isoformat(),
                    "total_commands": len(self.carrot_commands),
                    "current_index": self.current_carrot_index,
                    "current_cmd": self.current_carrot_cmd,
                    "current_arg": self.current_carrot_arg
                },
                "carrot_state": self.carrot_state,
                "commands": self.carrot_commands,
                "current_navigation_data": self.current_navigation_data
            }
            
            with open(filename, 'w', encoding='utf-8') as f:
                json.dump(export_data, f, indent=2, ensure_ascii=False)
                
        except Exception as e:
            raise Exception(f"JSON导出失败: {e}")
    
    def export_carrot_commands_csv(self, filename):
        """导出CarrotMan命令为CSV格式"""
        try:
            import csv
            
            with open(filename, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                writer.writerow(['序号', '时间', '索引', '命令', '参数', '来源IP', '原始数据'])
                
                for i, cmd in enumerate(self.carrot_commands, 1):
                    writer.writerow([
                        i,
                        cmd['time_str'],
                        cmd['carrotIndex'],
                        cmd['carrotCmd'],
                        cmd['carrotArg'],
                        cmd['source_ip'],
                        str(cmd['raw_data'])
                    ])
                    
        except Exception as e:
            raise Exception(f"CSV导出失败: {e}")
    
    def activate_sdi(self):
        """激活SDI状态"""
        self.carrot_state["active_sdi_count"] = self.carrot_state["active_sdi_count_max"]
        self.carrot_state["nSdiType"] = 1  # 固定测速
        self.carrot_state["nSdiSpeedLimit"] = 50
        self.carrot_state["nSdiDist"] = 300
        self.update_sdi_info()
        self.log_navigation_message("📷 SDI已激活: 固定测速 50km/h, 300m")
    
    def activate_kisa(self):
        """激活KISA状态"""
        self.carrot_state["active_kisa_count"] = 100
        self.log_navigation_message("🚨 KISA已激活")
    
    def simulate_turn(self):
        """模拟转弯事件"""
        turn_types = [12, 13, 16, 19]  # 左转、右转、急左转、急右转
        self.carrot_state["nTBTTurnType"] = random.choice(turn_types)
        self.carrot_state["nTBTDist"] = random.randint(100, 500)
        self.carrot_state["szTBTMainText"] = "前方转弯"
        self.update_tbt_info()
        self.log_navigation_message(f"🔄 模拟转弯: 类型={self.carrot_state['nTBTTurnType']}, 距离={self.carrot_state['nTBTDist']}m")
    
    def simulate_speed_limit(self):
        """模拟限速事件"""
        sdi_types = [1, 2, 7, 8]  # 固定测速、区间测速、违章摄像头、红绿灯摄像头
        self.carrot_state["nSdiType"] = random.choice(sdi_types)
        self.carrot_state["nSdiSpeedLimit"] = random.randint(30, 80)
        self.carrot_state["nSdiDist"] = random.randint(200, 800)
        self.update_sdi_info()
        self.log_navigation_message(f"🚦 模拟限速: 类型={self.carrot_state['nSdiType']}, 限速={self.carrot_state['nSdiSpeedLimit']}km/h, 距离={self.carrot_state['nSdiDist']}m")

    def log_message(self, message: str, msg_type: str = "INFO"):
        """Log message to GUI"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        formatted_msg = f"[{timestamp}] {msg_type}: {message}\n"

        if hasattr(self, 'msg_text'):
            self.msg_text.insert(tk.END, formatted_msg)
            self.msg_text.see(tk.END)

        print(formatted_msg.strip())

    def log_connection(self, message: str):
        """Log connection status"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        formatted_msg = f"[{timestamp}] {message}\n"

        if hasattr(self, 'conn_text'):
            self.conn_text.insert(tk.END, formatted_msg)
            self.conn_text.see(tk.END)

    def update_data_display(self):
        """Update data display tab"""
        if hasattr(self, 'data_text'):
            self.data_text.delete(1.0, tk.END)
            data_str = json.dumps(self.vehicle_data, indent=2, ensure_ascii=False)
            self.data_text.insert(tk.END, data_str)

    def start_simulator(self):
        """Start the simulator"""
        if self.is_running:
            return

        self.is_running = True
        self.start_btn.config(state=tk.DISABLED)
        self.stop_btn.config(state=tk.NORMAL)
        self.status_label.config(text="Status: Starting...")

        try:
            # Start all network services
            self.start_network_services()
            self.status_label.config(text="Status: Running")
            self.log_message("Comma3 Simulator started successfully")

        except Exception as e:
            self.log_message(f"Failed to start simulator: {e}", "ERROR")
            self.stop_simulator()

    def stop_simulator(self):
        """Stop the simulator"""
        self.is_running = False
        self.start_btn.config(state=tk.NORMAL)
        self.stop_btn.config(state=tk.DISABLED)
        self.status_label.config(text="Status: Stopping...")

        # Stop all threads
        for thread in self.threads:
            if thread.is_alive():
                thread.join(timeout=1.0)

        self.threads.clear()
        self.connected_clients.clear()
        self.status_label.config(text="Status: Stopped")
        self.log_message("Comma3 Simulator stopped")

    def start_network_services(self):
        """Start all network services"""
        services = [
            ("Broadcast Discovery", self.broadcast_service),
            ("Main Data Server", self.main_data_service),
            ("Route Data Server", self.route_data_service),
            ("ZMQ Command Server", self.zmq_command_service),
            ("KISA Data Server", self.kisa_data_service),
            ("Data Update Loop", self.data_update_loop)
        ]

        for name, service_func in services:
            try:
                thread = threading.Thread(target=service_func, daemon=True)
                thread.start()
                self.threads.append(thread)
                self.log_connection(f"✅ {name} started")
            except Exception as e:
                self.log_message(f"Failed to start {name}: {e}", "ERROR")
                raise

    def broadcast_service(self):
        """UDP broadcast discovery service (port 7705) - 基于CarrotMan逆向分析优化"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

            self.log_connection(f"📡 Broadcast service listening on port {self.broadcast_port}")

            while self.is_running:
                try:
                    # 创建广播消息 - 完全符合CarrotMan的make_send_message()方法
                    msg = self.make_send_message()

                    json_data = json.dumps(msg)
                    sock.sendto(json_data.encode('utf-8'), (self.broadcast_ip, self.broadcast_port))

                    time.sleep(0.1)  # 10Hz broadcast rate

                except Exception as e:
                    if self.is_running:
                        self.log_message(f"Broadcast error: {e}", "ERROR")
                    time.sleep(1)

        except Exception as e:
            self.log_message(f"Broadcast service failed: {e}", "ERROR")
        finally:
            try:
                sock.close()
            except Exception:
                pass

    def make_send_message(self) -> Dict[str, Any]:
        """创建发送消息 - 基于CarrotMan的make_send_message()方法"""
        msg = {}
        
        # 基础信息 - 完全符合原始实现
        msg['Carrot2'] = "0.9.4"  # 从Params("Version")获取，模拟OpenPilot版本
        msg['IsOnroad'] = self.vehicle_data["is_onroad"]
        msg['CarrotRouteActive'] = len(self.route_points) > 0
        msg['ip'] = self.local_ip
        msg['port'] = self.main_port
        
        # 车辆状态 - 基于carState数据
        msg['log_carrot'] = self.vehicle_data.get("log_carrot", "active")
        msg['v_cruise_kph'] = float(self.vehicle_data["v_cruise_kph"])
        msg['v_ego_kph'] = int(self.vehicle_data["v_ego_kph"])  # 取整，符合原始实现
        
        # CarrotMan状态 - 基于carrot_serv数据
        msg['tbt_dist'] = int(self.carrot_state.get("xDistToTurn", 0))
        msg['sdi_dist'] = int(self.carrot_state.get("xSpdDist", 0))
        msg['active'] = self.vehicle_data["controls_active"]
        msg['xState'] = self.carrot_state.get("xState", 0)
        msg['trafficState'] = self.carrot_state.get("trafficState", 0)
        
        return msg

    def update_carrot_state(self):
        """更新CarrotMan状态机 - 基于carrot_man.py逆向分析"""
        # 更新计数器
        self.carrot_state["active_count"] = max(self.carrot_state["active_count"] - 1, 0)
        self.carrot_state["active_sdi_count"] = max(self.carrot_state["active_sdi_count"] - 1, 0)
        self.carrot_state["active_kisa_count"] = max(self.carrot_state["active_kisa_count"] - 1, 0)
        
        # 更新active_carrot状态机
        if self.carrot_state["active_kisa_count"] > 0:
            self.carrot_state["active_carrot"] = 2
        elif self.carrot_state["active_count"] > 0:
            self.carrot_state["active_carrot"] = 2 if self.carrot_state["active_sdi_count"] > 0 else 1
        else:
            self.carrot_state["active_carrot"] = 0
        
        # 更新距离递减
        if self.vehicle_data["v_ego_kph"] > 0:
            delta_dist = self.vehicle_data["v_ego_kph"] / 3.6 * 0.1  # 100ms更新间隔
            self.carrot_state["xSpdDist"] = max(self.carrot_state["xSpdDist"] - delta_dist, -1000)
            self.carrot_state["xDistToTurn"] = max(self.carrot_state["xDistToTurn"] - delta_dist, -50)
            self.carrot_state["xDistToTurnNext"] = max(self.carrot_state["xDistToTurnNext"] - delta_dist, -50)
        
        # 更新倒计时
        self.update_countdown_timers()
        
        # 更新交通灯状态
        self.update_traffic_light_state()

    def update_countdown_timers(self):
        """更新倒计时定时器 - 基于CarrotMan逻辑"""
        v_ego = self.vehicle_data["v_ego_kph"] / 3.6  # 转换为m/s
        
        # 速度倒计时
        if self.carrot_state["xSpdDist"] > 0 and v_ego > 0:
            self.carrot_state["left_spd_sec"] = int(max(self.carrot_state["xSpdDist"] - v_ego, 1) / max(1, v_ego) + 0.5)
        else:
            self.carrot_state["left_spd_sec"] = 100
        
        # 转弯倒计时
        if self.carrot_state["xDistToTurn"] > 0 and v_ego > 0:
            self.carrot_state["left_tbt_sec"] = int(max(self.carrot_state["xDistToTurn"] - v_ego, 1) / max(1, v_ego) + 0.5)
        else:
            self.carrot_state["left_tbt_sec"] = 100
        
        # 显示倒计时逻辑
        left_sec = min(self.carrot_state["left_spd_sec"], self.carrot_state["left_tbt_sec"])
        
        if left_sec > 11:
            self.carrot_state["left_sec"] = 100
            self.carrot_state["max_left_sec"] = 100
        else:
            self.carrot_state["max_left_sec"] = min(11, max(6, int(self.vehicle_data["v_ego_kph"]/10) + 1))
            
            if left_sec == self.carrot_state["max_left_sec"] and self.carrot_state["sdi_inform"]:
                self.carrot_state["carrot_left_sec"] = 11
            elif 1 <= left_sec < self.carrot_state["max_left_sec"]:
                self.carrot_state["carrot_left_sec"] = left_sec
            elif left_sec == 0 and self.carrot_state["left_sec"] == 1:
                self.carrot_state["carrot_left_sec"] = left_sec
            
            self.carrot_state["left_sec"] = left_sec

    def update_traffic_light_state(self):
        """更新交通灯状态"""
        self.carrot_state["traffic_light_count"] -= 1
        if self.carrot_state["traffic_light_count"] < 0:
            self.carrot_state["traffic_light_count"] = -1
            self.carrot_state["traffic_state"] = 0

    def main_data_service(self):
        """Main UDP data communication service (port 7706)"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.bind(('0.0.0.0', self.main_port))
            sock.settimeout(1.0)

            self.log_connection(f"🔌 Main data service listening on port {self.main_port}")

            while self.is_running:
                try:
                    data, addr = sock.recvfrom(4096)

                    if addr not in self.connected_clients:
                        self.connected_clients.append(addr)
                        self.log_connection(f"📱 Client connected: {addr[0]}:{addr[1]}")

                    # Parse received JSON data
                    try:
                        json_data = json.loads(data.decode('utf-8'))
                        self.process_received_data(json_data, addr)
                    except json.JSONDecodeError as e:
                        self.log_message(f"Invalid JSON from {addr}: {e}", "WARN")

                except socket.timeout:
                    continue
                except Exception as e:
                    if self.is_running:
                        self.log_message(f"Main data service error: {e}", "ERROR")

        except Exception as e:
            self.log_message(f"Main data service failed: {e}", "ERROR")
        finally:
            try:
                sock.close()
            except Exception:
                pass

    def process_received_data(self, data: Dict[str, Any], addr: Tuple[str, int]):
        """处理接收到的JSON数据 - 基于CarrotMan的update()方法优化"""
        try:
            # 记录接收数据
            self.log_message(f"📥 Received data from {addr[0]}: {len(str(data))} bytes")

            # 存储导航数据用于分析
            navigation_entry = {
                "timestamp": time.time(),
                "source_ip": addr[0],
                "source_port": addr[1],
                "data": data.copy(),
                "data_size": len(str(data))
            }
            self.received_navigation_data.append(navigation_entry)
            
            # 为CarrotMan命令处理添加source_ip信息
            data_with_source = data.copy()
            data_with_source["source_ip"] = addr[0]
            data_with_source["source_port"] = addr[1]

            # 更新导航统计
            self.navigation_statistics["total_messages"] += 1

            # 分析消息类型
            msg_type = self.analyze_message_type(data)
            if msg_type in self.navigation_statistics["message_types"]:
                self.navigation_statistics["message_types"][msg_type] += 1
            else:
                self.navigation_statistics["message_types"][msg_type] = 1

            # 记录导航消息
            self.log_navigation_message(f"📡 收到{msg_type}数据: {len(str(data))}字节 来自{addr[0]}")

            # 更新当前导航数据显示
            self.update_current_navigation_data(data)

            # 基于CarrotMan逻辑处理数据
            self.process_carrot_data(data_with_source)

            # 调度优化的GUI更新
            current_time = time.time()
            if current_time - self.last_gui_update > 0.1:  # 限制到10Hz
                self.last_gui_update = current_time
                self.schedule_gui_updates()
                # 更新独立实时数据窗口
                if self.realtime_window and self.realtime_window.window:
                    self.realtime_window.update_display(self.current_navigation_data)
            else:
                self.pending_updates = True

        except Exception as e:
            self.log_message(f"Error processing data: {e}", "ERROR")
            self.log_navigation_message(f"❌ 数据处理错误: {e}")

    def update_current_navigation_data(self, data: Dict[str, Any]):
        """更新当前导航数据用于优化显示 - 包含CarrotMan状态和缺失字段检测"""
        try:
            # 获取所有定义的字段
            field_defs = self.get_navigation_field_definitions()
            
            # 清空当前数据
            self.current_navigation_data.clear()
            
            # 添加UDP广播字段 (从vehicle_data和carrot_state获取)
            broadcast_fields = {
                "Carrot2": "Comma3 Simulator v1.0",
                "IsOnroad": self.vehicle_data.get("is_onroad", False),
                "CarrotRouteActive": len(self.route_points) > 0,
                "ip": self.local_ip,
                "port": self.main_port,
                "log_carrot": self.vehicle_data.get("log_carrot", ""),
                "v_cruise_kph": self.vehicle_data.get("v_cruise_kph", 0),
                "v_ego_kph": self.vehicle_data.get("v_ego_kph", 0),
                "tbt_dist": self.carrot_state.get("xDistToTurn", 0),
                "sdi_dist": self.carrot_state.get("xSpdDist", 0),
                "active": self.vehicle_data.get("controls_active", False),
                "xState": self.carrot_state.get("xState", 0),
                "trafficState": self.carrot_state.get("trafficState", 0)
            }
            
            for field, value in broadcast_fields.items():
                self.current_navigation_data[field] = value
            
            # 添加手机应用发送的字段
            for field, value in data.items():
                if field in field_defs:
                    self.current_navigation_data[field] = value
                    
            # 添加内部计算字段 (carrotMan消息)
            internal_fields = {
                "activeCarrot": self.carrot_state.get("active_carrot", 0),
                "nRoadLimitSpeed": self.vehicle_data.get("road_limit_speed", 0),
                "xSpdType": self.carrot_state.get("xSpdType", 0),
                "xSpdLimit": self.carrot_state.get("xSpdLimit", 0),
                "xSpdDist": self.carrot_state.get("xSpdDist", 0),
                "xSpdCountDown": self.carrot_state.get("left_spd_sec", 0),
                "xTurnInfo": self.carrot_state.get("xTurnInfo", 0),
                "xDistToTurn": self.carrot_state.get("xDistToTurn", 0),
                "xTurnCountDown": self.carrot_state.get("left_tbt_sec", 0),
                "atcType": self.carrot_state.get("atcType", ""),
                "vTurnSpeed": self.carrot_state.get("vTurnSpeed", 0),
                "szPosRoadName": self.carrot_state.get("szPosRoadName", ""),
                "szTBTMainText": self.carrot_state.get("szTBTMainText", ""),
                "desiredSpeed": self.carrot_state.get("desiredSpeed", 0),
                "desiredSource": self.carrot_state.get("desiredSource", ""),
                "carrotCmdIndex": self.carrot_state.get("carrotCmdIndex", 0),
                "carrotCmd": self.carrot_state.get("carrotCmd", ""),
                "carrotArg": self.carrot_state.get("carrotArg", ""),
                "trafficState": self.carrot_state.get("traffic_state", 0),
                "xPosSpeed": self.vehicle_data.get("v_ego_kph", 0),
                "xPosAngle": self.carrot_state.get("bearing", 0),
                "xPosLat": self.carrot_state.get("vpPosPointLatNavi", 0),
                "xPosLon": self.carrot_state.get("vpPosPointLonNavi", 0),
                "nGoPosDist": self.carrot_state.get("nGoPosDist", 0),
                "nGoPosTime": self.carrot_state.get("nGoPosTime", 0),
                "szSdiDescr": self.carrot_state.get("szSdiDescr", ""),
                "leftSec": self.carrot_state.get("carrot_left_sec", 0)
            }
            
            for field, value in internal_fields.items():
                self.current_navigation_data[field] = value
            
            # 添加CarrotMan内部状态字段
            for field, value in self.carrot_state.items():
                if field.startswith(('active_', 'left_', 'traffic_light_')):
                    self.current_navigation_data[f"carrot_{field}"] = value
            
            # 添加其他元数据字段
            other_fields = ['timestamp', 'message_type', 'data_size']
            for field in other_fields:
                if field in data:
                    self.current_navigation_data[field] = data[field]
                    
        except Exception as e:
            self.log_navigation_message(f"❌ 当前数据更新错误: {e}")

    def process_carrot_data(self, data: Dict[str, Any]):
        """基于CarrotMan逻辑处理数据"""
        try:
            # 专门处理CarrotMan命令字段
            self.process_carrot_commands(data)
            
            # 更新carrotIndex
            if "carrotIndex" in data:
                self.carrot_state["carrotIndex"] = int(data.get("carrotIndex"))

            # 处理命令
            if "carrotCmd" in data:
                self.carrot_state["carrotCmdIndex"] = self.carrot_state["carrotIndex"]
                self.carrot_state["carrotCmd"] = data.get("carrotCmd", "")
                self.carrot_state["carrotArg"] = data.get("carrotArg", "")
                self.log_navigation_message(f"🔧 收到命令: {self.carrot_state['carrotCmd']} {self.carrot_state['carrotArg']}")

            # 激活计数器
            self.carrot_state["active_count"] = 80

            # 处理目标位置
            if "goalPosX" in data:
                self.carrot_state["goalPosX"] = float(data.get("goalPosX", self.carrot_state["goalPosX"]))
                self.carrot_state["goalPosY"] = float(data.get("goalPosY", self.carrot_state["goalPosY"]))
                self.carrot_state["szGoalName"] = data.get("szGoalName", self.carrot_state["szGoalName"])

            # 处理导航数据
            if "nRoadLimitSpeed" in data:
                self.process_navigation_data(data)

            # 处理GPS数据
            if "latitude" in data:
                self.process_gps_data(data)

        except Exception as e:
            self.log_navigation_message(f"❌ Carrot数据处理错误: {e}")
    
    def process_carrot_commands(self, data: Dict[str, Any]):
        """专门处理CarrotMan命令字段 - 实时解析和跟踪"""
        try:
            current_time = time.time()
            timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
            
            # 检查是否有新的命令数据
            carrot_index = data.get("carrotIndex", 0)
            carrot_cmd = data.get("carrotCmd", "")
            carrot_arg = data.get("carrotArg", "")
            
            # 更新当前命令状态
            if carrot_index > 0:
                self.current_carrot_index = int(carrot_index)
                
            if carrot_cmd:
                self.current_carrot_cmd = str(carrot_cmd)
                
            if carrot_arg:
                self.current_carrot_arg = str(carrot_arg)
            
            # 检查是否有新的命令（通过索引变化判断）
            if (carrot_index > self.last_carrot_cmd_index and 
                carrot_cmd and carrot_cmd.strip()):
                
                # 创建命令记录
                command_record = {
                    "timestamp": current_time,
                    "time_str": timestamp,
                    "carrotIndex": int(carrot_index),
                    "carrotCmd": str(carrot_cmd),
                    "carrotArg": str(carrot_arg),
                    "source_ip": data.get("source_ip", "unknown"),
                    "raw_data": data.copy()
                }
                
                # 添加到命令历史
                self.carrot_commands.append(command_record)
                
                # 更新上次命令索引
                self.last_carrot_cmd_index = int(carrot_index)
                
                # 记录日志
                self.log_navigation_message(
                    f"🔧 CarrotMan命令 #{carrot_index}: {carrot_cmd} | {carrot_arg}"
                )
                
                # 解析命令类型
                self.analyze_carrot_command(carrot_cmd, carrot_arg, command_record)
                
        except Exception as e:
            self.log_navigation_message(f"❌ CarrotMan命令解析错误: {e}")
    
    def analyze_carrot_command(self, cmd: str, arg: str, record: Dict[str, Any]):
        """分析CarrotMan命令类型和参数"""
        try:
            cmd_lower = cmd.lower().strip()
            
            # 命令类型分析
            if "detect" in cmd_lower:
                self.analyze_detect_command(arg, record)
            elif "set" in cmd_lower:
                self.analyze_set_command(arg, record)
            elif "get" in cmd_lower:
                self.analyze_get_command(arg, record)
            elif "reset" in cmd_lower:
                self.analyze_reset_command(arg, record)
            else:
                self.log_navigation_message(f"🔍 未知命令类型: {cmd}")
                
        except Exception as e:
            self.log_navigation_message(f"❌ 命令分析错误: {e}")
    
    def analyze_detect_command(self, arg: str, record: Dict[str, Any]):
        """分析DETECT命令"""
        try:
            if "red light" in arg.lower():
                self.log_navigation_message("🚦 检测到红灯信号")
                self.carrot_state["traffic_state"] = 1
            elif "green light" in arg.lower():
                self.log_navigation_message("🚦 检测到绿灯信号")
                self.carrot_state["traffic_state"] = 2
            elif "yellow light" in arg.lower():
                self.log_navigation_message("🚦 检测到黄灯信号")
                self.carrot_state["traffic_state"] = 3
            else:
                self.log_navigation_message(f"🔍 DETECT命令: {arg}")
                
        except Exception as e:
            self.log_navigation_message(f"❌ DETECT命令分析错误: {e}")
    
    def analyze_set_command(self, arg: str, record: Dict[str, Any]):
        """分析SET命令"""
        try:
            self.log_navigation_message(f"⚙️ SET命令: {arg}")
            # 可以在这里添加具体的SET命令处理逻辑
            
        except Exception as e:
            self.log_navigation_message(f"❌ SET命令分析错误: {e}")
    
    def analyze_get_command(self, arg: str, record: Dict[str, Any]):
        """分析GET命令"""
        try:
            self.log_navigation_message(f"📊 GET命令: {arg}")
            # 可以在这里添加具体的GET命令处理逻辑
            
        except Exception as e:
            self.log_navigation_message(f"❌ GET命令分析错误: {e}")
    
    def analyze_reset_command(self, arg: str, record: Dict[str, Any]):
        """分析RESET命令"""
        try:
            self.log_navigation_message(f"🔄 RESET命令: {arg}")
            # 可以在这里添加具体的RESET命令处理逻辑
            
        except Exception as e:
            self.log_navigation_message(f"❌ RESET命令分析错误: {e}")

    def process_navigation_data(self, data: Dict[str, Any]):
        """处理导航数据 - 基于CarrotServ.update()方法完整实现"""
        try:
            # 激活SDI计数器
            self.carrot_state["active_sdi_count"] = self.carrot_state["active_sdi_count_max"]

            # 处理道路限速 - 基于原始编码逻辑
            nRoadLimitSpeed = int(data.get("nRoadLimitSpeed", 20))
            if nRoadLimitSpeed > 0:
                if nRoadLimitSpeed > 200:
                    # 编码格式: (speed - 20) / 10
                    nRoadLimitSpeed = (nRoadLimitSpeed - 20) / 10
                elif nRoadLimitSpeed == 120:
                    nRoadLimitSpeed = 30
            else:
                nRoadLimitSpeed = 30
            
            self.vehicle_data["road_limit_speed"] = nRoadLimitSpeed
            self.carrot_state["nRoadLimitSpeed"] = nRoadLimitSpeed
            self.log_navigation_message(f"🚦 限速更新: {nRoadLimitSpeed} km/h")

            # 处理SDI参数 - 完整字段映射
            self.carrot_state["nSdiType"] = int(data.get("nSdiType", -1))
            self.carrot_state["nSdiSpeedLimit"] = int(data.get("nSdiSpeedLimit", 0))
            self.carrot_state["nSdiSection"] = int(data.get("nSdiSection", -1))
            self.carrot_state["nSdiDist"] = int(data.get("nSdiDist", -1))
            self.carrot_state["nSdiBlockType"] = int(data.get("nSdiBlockType", -1))
            self.carrot_state["nSdiBlockSpeed"] = int(data.get("nSdiBlockSpeed", 0))
            self.carrot_state["nSdiBlockDist"] = int(data.get("nSdiBlockDist", 0))

            # 处理SDI Plus参数
            self.carrot_state["nSdiPlusType"] = int(data.get("nSdiPlusType", -1))
            self.carrot_state["nSdiPlusSpeedLimit"] = int(data.get("nSdiPlusSpeedLimit", 0))
            self.carrot_state["nSdiPlusDist"] = int(data.get("nSdiPlusDist", 0))
            self.carrot_state["nSdiPlusBlockType"] = int(data.get("nSdiPlusBlockType", -1))
            self.carrot_state["nSdiPlusBlockSpeed"] = int(data.get("nSdiPlusBlockSpeed", 0))
            self.carrot_state["nSdiPlusBlockDist"] = int(data.get("nSdiPlusBlockDist", 0))

            # 处理TBT参数 - 基于原始字段名
            self.carrot_state["nTBTDist"] = int(data.get("nTBTDist", 0))
            self.carrot_state["nTBTTurnType"] = int(data.get("nTBTTurnType", -1))
            self.carrot_state["szTBTMainText"] = data.get("szTBTMainText", "")
            self.carrot_state["szNearDirName"] = data.get("szNearDirName", "")
            self.carrot_state["szFarDirName"] = data.get("szFarDirName", "")
            self.carrot_state["nTBTNextRoadWidth"] = int(data.get("nTBTNextRoadWidth", 0))

            # 处理下一个转弯
            self.carrot_state["nTBTDistNext"] = int(data.get("nTBTDistNext", 0))
            self.carrot_state["nTBTTurnTypeNext"] = int(data.get("nTBTTurnTypeNext", -1))
            self.carrot_state["szTBTMainTextNext"] = data.get("szTBTMainTextNext", "")

            # 处理目的地信息
            self.carrot_state["nGoPosDist"] = int(data.get("nGoPosDist", 0))
            self.carrot_state["nGoPosTime"] = int(data.get("nGoPosTime", 0))
            self.carrot_state["szPosRoadName"] = data.get("szPosRoadName", "")
            if self.carrot_state["szPosRoadName"] == "null":
                self.carrot_state["szPosRoadName"] = ""

            # 处理GPS位置 - 基于原始GPS融合逻辑
            vpPosPointLat = float(data.get("vpPosPointLat", 0.0))
            vpPosPointLon = float(data.get("vpPosPointLon", 0.0))
            if vpPosPointLat != 0.0:
                self.carrot_state["vpPosPointLatNavi"] = vpPosPointLat
                self.carrot_state["vpPosPointLonNavi"] = vpPosPointLon
                self.carrot_state["last_update_gps_time_navi"] = time.monotonic()
                self.carrot_state["nPosAngle"] = float(data.get("nPosAngle", self.carrot_state["nPosAngle"]))

            self.carrot_state["nPosSpeed"] = float(data.get("nPosSpeed", self.carrot_state["nPosSpeed"]))

            # 更新转弯信息
            self.update_tbt_info()
            
            # 更新SDI信息
            self.update_sdi_info()

            self.log_navigation_message(
                f"📊 SDI: {self.carrot_state['nSdiType']}, {self.carrot_state['nSdiSpeedLimit']}, "
                f"TBT: {self.carrot_state['nTBTTurnType']}, {self.carrot_state['nTBTDist']}"
            )

        except Exception as e:
            self.log_navigation_message(f"❌ 导航数据处理错误: {e}")

    def process_gps_data(self, data: Dict[str, Any]):
        """处理GPS数据 - 基于CarrotMan逻辑"""
        try:
            now = time.monotonic()
            self.carrot_state["nPosAnglePhone"] = float(data.get("heading", self.carrot_state["nPosAngle"]))
            
            # 3秒内导航数据没有更新时，使用手机GPS
            if (now - self.carrot_state["last_update_gps_time_navi"]) > 3.0:
                self.carrot_state["vpPosPointLatNavi"] = float(data.get("latitude", self.carrot_state["vpPosPointLatNavi"]))
                self.carrot_state["vpPosPointLonNavi"] = float(data.get("longitude", self.carrot_state["vpPosPointLonNavi"]))
                self.carrot_state["nPosAngle"] = self.carrot_state["nPosAnglePhone"]
                self.carrot_state["last_update_gps_time_phone"] = now
                self.carrot_state["gps_accuracy_phone"] = float(data.get("accuracy", 0))
                self.carrot_state["nPosSpeed"] = float(data.get("gps_speed", 0))
                
                self.log_navigation_message(
                    f"📱 手机GPS: {self.carrot_state['vpPosPointLatNavi']:.6f}, "
                    f"{self.carrot_state['vpPosPointLonNavi']:.6f}, "
                    f"精度: {self.carrot_state['gps_accuracy_phone']}m"
                )

        except Exception as e:
            self.log_navigation_message(f"❌ GPS数据处理错误: {e}")

    def update_tbt_info(self):
        """更新转弯信息 - 基于CarrotMan的_update_tbt()方法"""
        # 转弯类型映射
        turn_type_mapping = {
            12: ("turn", "left", 1),
            16: ("turn", "sharp left", 1),
            13: ("turn", "right", 2),
            19: ("turn", "sharp right", 2),
            102: ("off ramp", "slight left", 3),
            105: ("off ramp", "slight left", 3),
            112: ("off ramp", "slight left", 3),
            115: ("off ramp", "slight left", 3),
            101: ("off ramp", "slight right", 4),
            104: ("off ramp", "slight right", 4),
            111: ("off ramp", "slight right", 4),
            114: ("off ramp", "slight right", 4),
            7: ("fork", "left", 3),
            44: ("fork", "left", 3),
            17: ("fork", "left", 3),
            75: ("fork", "left", 3),
            76: ("fork", "left", 3),
            118: ("fork", "left", 3),
            6: ("fork", "right", 4),
            43: ("fork", "right", 4),
            73: ("fork", "right", 4),
            74: ("fork", "right", 4),
            123: ("fork", "right", 4),
            124: ("fork", "right", 4),
            117: ("fork", "right", 4),
            131: ("rotary", "slight right", 5),
            132: ("rotary", "slight right", 5),
            140: ("rotary", "slight left", 5),
            141: ("rotary", "slight left", 5),
            133: ("rotary", "right", 5),
            134: ("rotary", "sharp right", 5),
            135: ("rotary", "sharp right", 5),
            136: ("rotary", "sharp left", 5),
            137: ("rotary", "sharp left", 5),
            138: ("rotary", "sharp left", 5),
            139: ("rotary", "left", 5),
            142: ("rotary", "straight", 5),
            14: ("turn", "uturn", 7),
            201: ("arrive", "straight", 8),
            51: ("notification", "straight", 0),
            52: ("notification", "straight", 0),
            53: ("notification", "straight", 0),
            54: ("notification", "straight", 0),
            55: ("notification", "straight", 0),
            153: ("", "", 6),  # TG
            154: ("", "", 6),  # TG
            249: ("", "", 6)   # TG
        }

        # 更新当前转弯信息
        if self.carrot_state["nTBTTurnType"] in turn_type_mapping:
            self.carrot_state["navType"], self.carrot_state["navModifier"], self.carrot_state["xTurnInfo"] = turn_type_mapping[self.carrot_state["nTBTTurnType"]]
        else:
            self.carrot_state["navType"], self.carrot_state["navModifier"], self.carrot_state["xTurnInfo"] = "invalid", "", -1

        # 更新下一个转弯信息
        if self.carrot_state["nTBTTurnTypeNext"] in turn_type_mapping:
            self.carrot_state["navTypeNext"], self.carrot_state["navModifierNext"], self.carrot_state["xTurnInfoNext"] = turn_type_mapping[self.carrot_state["nTBTTurnTypeNext"]]
        else:
            self.carrot_state["navTypeNext"], self.carrot_state["navModifierNext"], self.carrot_state["xTurnInfoNext"] = "invalid", "", -1

        # 更新转弯距离
        if self.carrot_state["nTBTDist"] > 0 and self.carrot_state["xTurnInfo"] > 0:
            self.carrot_state["xDistToTurn"] = self.carrot_state["nTBTDist"]
        if self.carrot_state["nTBTDistNext"] > 0 and self.carrot_state["xTurnInfoNext"] > 0:
            self.carrot_state["xDistToTurnNext"] = self.carrot_state["nTBTDistNext"] + self.carrot_state["nTBTDist"]

    def update_sdi_info(self):
        """更新SDI信息 - 基于CarrotMan的_update_sdi()方法"""
        # SDI类型处理逻辑
        if (self.carrot_state["nSdiType"] in [0, 1, 2, 3, 4, 7, 8, 75, 76] and 
            self.carrot_state["nSdiSpeedLimit"] > 0):
            self.carrot_state["xSpdLimit"] = self.carrot_state["nSdiSpeedLimit"] * 0.95  # 安全系数
            self.carrot_state["xSpdDist"] = self.carrot_state["nSdiDist"]
            self.carrot_state["xSpdType"] = self.carrot_state["nSdiType"]
            
            if self.carrot_state["nSdiBlockType"] in [2, 3]:
                self.carrot_state["xSpdDist"] = self.carrot_state["nSdiBlockDist"]
                self.carrot_state["xSpdType"] = 4
                
        elif (self.carrot_state["nSdiPlusType"] == 22 or self.carrot_state["nSdiType"] == 22) and self.carrot_state["roadcate"] > 1:
            # 减速带处理
            self.carrot_state["xSpdLimit"] = 30  # 减速带速度
            self.carrot_state["xSpdDist"] = self.carrot_state["nSdiPlusDist"] if self.carrot_state["nSdiPlusType"] == 22 else self.carrot_state["nSdiDist"]
            self.carrot_state["xSpdType"] = 22
        else:
            self.carrot_state["xSpdLimit"] = 0
            self.carrot_state["xSpdType"] = -1
            self.carrot_state["xSpdDist"] = 0

    def schedule_gui_updates(self):
        """Schedule GUI updates optimally with anti-flickering"""
        try:
            if hasattr(self, 'root') and not self.is_paused:
                # Use after_idle to prevent blocking and reduce flickering
                self.root.after_idle(self.update_data_display)
                self.root.after_idle(self.update_navigation_statistics)
                # Only update navigation display if not paused
                if not self.is_paused:
                    self.root.after_idle(self.refresh_navigation_display)
        except Exception as e:
            self.log_navigation_message(f"❌ GUI更新调度错误: {e}")

    def process_pending_updates(self):
        """Process any pending GUI updates"""
        if self.pending_updates:
            self.pending_updates = False
            self.schedule_gui_updates()

    def route_data_service(self):
        """TCP route data service (port 7709)"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('0.0.0.0', self.route_port))
            sock.listen(5)
            sock.settimeout(1.0)

            self.log_connection(f"🛣️ Route data service listening on port {self.route_port}")

            while self.is_running:
                try:
                    conn, addr = sock.accept()
                    self.log_connection(f"📍 Route client connected: {addr[0]}:{addr[1]}")

                    # Handle route data in separate thread
                    route_thread = threading.Thread(
                        target=self.handle_route_client,
                        args=(conn, addr),
                        daemon=True
                    )
                    route_thread.start()

                except socket.timeout:
                    continue
                except Exception as e:
                    if self.is_running:
                        self.log_message(f"Route service error: {e}", "ERROR")

        except Exception as e:
            self.log_message(f"Route service failed: {e}", "ERROR")
        finally:
            try:
                sock.close()
            except Exception:
                pass

    def handle_route_client(self, conn: socket.socket, addr: Tuple[str, int]):
        """Handle individual route client connection - 基于CarrotMan.carrot_route()方法"""
        try:
            self.log_message(f"📍 处理路线客户端: {addr[0]}:{addr[1]}")
            
            # 接收总数据大小 (4字节，大端序)
            total_size_bytes = self.recv_all(conn, 4)
            if not total_size_bytes:
                self.log_message("Connection closed or error occurred")
                return
                
            total_size = struct.unpack('!I', total_size_bytes)[0]
            self.log_message(f"📊 Receiving route data: {total_size} bytes")

            # 接收所有路线数据
            all_data = self.recv_all(conn, total_size)
            if not all_data:
                self.log_message("Connection closed or incomplete data received")
                return

            # 解析路线点 - 基于原始实现
            self.route_points = []
            points = []
            for i in range(0, len(all_data), 8):
                if i + 8 <= len(all_data):
                    x, y = struct.unpack('!ff', all_data[i:i+8])
                    self.route_points.append((x, y))
                    # 模拟Coordinate对象创建
                    coord_dict = {"latitude": y, "longitude": x}
                    points.append(coord_dict)

            # 更新路线状态 - 基于原始实现
            self.navi_points_start_index = 0
            self.navi_points_active = True
            
            self.log_message(f"📍 Received {len(self.route_points)} route points")
            self.log_message(f"📍 Route active: {self.navi_points_active}")

            # 更新GUI
            if hasattr(self, 'root'):
                self.root.after(0, self.update_data_display)

        except Exception as e:
            self.log_message(f"Route client error: {e}", "ERROR")
        finally:
            try:
                conn.close()
            except Exception:
                pass

    def recv_all(self, sock: socket.socket, length: int) -> bytes:
        """Receive exactly length bytes from socket"""
        data = b""
        while len(data) < length:
            packet = sock.recv(length - len(data))
            if not packet:
                return None
            data += packet
        return data

    def zmq_command_service(self):
        """ZMQ command interface service (port 7710)"""
        try:
            # Simple TCP server for ZMQ simulation
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('0.0.0.0', self.zmq_port))
            sock.listen(5)
            sock.settimeout(1.0)

            self.log_connection(f"⚡ ZMQ command service listening on port {self.zmq_port}")

            while self.is_running:
                try:
                    conn, addr = sock.accept()
                    self.log_connection(f"🔧 Command client connected: {addr[0]}:{addr[1]}")

                    # Handle command in separate thread
                    cmd_thread = threading.Thread(
                        target=self.handle_command_client,
                        args=(conn, addr),
                        daemon=True
                    )
                    cmd_thread.start()

                except socket.timeout:
                    continue
                except Exception as e:
                    if self.is_running:
                        self.log_message(f"ZMQ service error: {e}", "ERROR")

        except Exception as e:
            self.log_message(f"ZMQ service failed: {e}", "ERROR")
        finally:
            try:
                sock.close()
            except Exception:
                pass

    def handle_command_client(self, conn: socket.socket, addr: Tuple[str, int]):
        """Handle ZMQ command client"""
        try:
            self.log_message(f"⚡ 处理命令客户端: {addr[0]}:{addr[1]}")
            while self.is_running:
                data = conn.recv(1024)
                if not data:
                    break

                try:
                    cmd_data = json.loads(data.decode('utf-8'))
                    response = self.process_command(cmd_data)
                    conn.send(json.dumps(response).encode('utf-8'))

                except json.JSONDecodeError:
                    error_response = {"error": "Invalid JSON"}
                    conn.send(json.dumps(error_response).encode('utf-8'))

        except Exception as e:
            self.log_message(f"Command client error: {e}", "ERROR")
        finally:
            try:
                conn.close()
            except Exception:
                pass

    def process_command(self, cmd_data: Dict[str, Any]) -> Dict[str, Any]:
        """Process ZMQ command"""
        try:
            if "echo_cmd" in cmd_data:
                # Simulate shell command execution
                command = cmd_data["echo_cmd"]
                self.log_message(f"🔧 Executing command: {command}")

                # Simulate command responses
                if "status" in command.lower():
                    result = "Comma3 Simulator Status: Running"
                elif "version" in command.lower():
                    result = "Comma3 Simulator v1.0"
                else:
                    result = f"Simulated output for: {command}"

                return {
                    "echo_cmd": command,
                    "exitStatus": 0,
                    "result": result,
                    "error": ""
                }

            elif "tmux_send" in cmd_data:
                # Simulate tmux data upload
                password = cmd_data["tmux_send"]
                self.log_message(f"📤 Simulating tmux upload with password: {password[:4]}...")

                return {
                    "tmux_send": password,
                    "result": "success"
                }

            else:
                return {"error": "Unknown command"}

        except Exception as e:
            return {"error": str(e)}

    def kisa_data_service(self):
        """KISA data service (port 12345)"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.bind(('0.0.0.0', self.kisa_port))
            sock.settimeout(1.0)

            self.log_connection(f"🚨 KISA data service listening on port {self.kisa_port}")

            while self.is_running:
                try:
                    data, addr = sock.recvfrom(4096)

                    # Parse KISA data format (key:value/key:value)
                    kisa_data = self.parse_kisa_data(data)
                    if kisa_data:
                        self.log_message(f"🚨 KISA data from {addr[0]}: {kisa_data}")
                        self.process_kisa_data(kisa_data)

                except socket.timeout:
                    continue
                except Exception as e:
                    if self.is_running:
                        self.log_message(f"KISA service error: {e}", "ERROR")

        except Exception as e:
            self.log_message(f"KISA service failed: {e}", "ERROR")
        finally:
            try:
                sock.close()
            except Exception:
                pass

    def parse_kisa_data(self, data: bytes) -> Dict[str, Any]:
        """Parse KISA data format"""
        try:
            decoded = data.decode('utf-8')
            result = {}

            parts = decoded.split('/')
            for part in parts:
                if ':' in part:
                    key, value = part.split(':', 1)
                    try:
                        result[key] = int(value)
                    except ValueError:
                        result[key] = value

            return result

        except Exception as e:
            self.log_message(f"KISA parse error: {e}", "ERROR")
            return {}

    def process_kisa_data(self, kisa_data: Dict[str, Any]):
        """处理KISA数据 - 基于CarrotServ.update_kisa()方法完整实现"""
        try:
            # 激活KISA计数器 - 基于原始实现
            self.carrot_state["active_kisa_count"] = 100

            # 处理当前速度
            if "kisawazecurrentspd" in kisa_data:
                self.vehicle_data["v_ego_kph"] = kisa_data["kisawazecurrentspd"]
                self.log_navigation_message(f"📱 KISA当前速度: {kisa_data['kisawazecurrentspd']} km/h")

            # 处理道路限速 - 基于原始单位转换逻辑
            if "kisawazeroadspdlimit" in kisa_data:
                road_limit_speed = kisa_data["kisawazeroadspdlimit"]
                if road_limit_speed > 0:
                    # 原始实现中的单位转换逻辑
                    if not self.carrot_state.get("is_metric", True):
                        road_limit_speed *= 1.60934  # MPH_TO_KPH conversion
                    self.vehicle_data["road_limit_speed"] = road_limit_speed
                    self.carrot_state["nRoadLimitSpeed"] = road_limit_speed
                    self.log_navigation_message(f"🚦 KISA道路限速: {road_limit_speed} km/h")

            # 处理道路名称
            if "kisawazeroadname" in kisa_data:
                road_name = kisa_data["kisawazeroadname"]
                self.vehicle_data["road_name"] = road_name
                self.carrot_state["szPosRoadName"] = road_name
                self.log_navigation_message(f"🛣️ KISA道路名称: {road_name}")

            # 处理Waze报告 - 基于原始正则表达式和类型映射
            if "kisawazereportid" in kisa_data and "kisawazealertdist" in kisa_data:
                id_str = kisa_data["kisawazereportid"]
                dist_str = kisa_data["kisawazealertdist"].lower()
                
                import re
                match = re.search(r'(\d+)', dist_str)
                distance = int(match.group(1)) if match else 0
                
                # 单位转换 - 基于原始实现
                if not self.carrot_state.get("is_metric", True):
                    distance = int(distance * 0.3048)  # feet to meters
                
                xSpdType = -1
                if 'camera' in id_str:
                    xSpdType = 101    # 101: waze speed cam
                elif 'police' in id_str:
                    xSpdType = 100    # 100: police

                if xSpdType >= 0:
                    # 基于原始偏移计算
                    offset = 5 if self.carrot_state.get("is_metric", True) else 5 * 1.60934
                    self.carrot_state["xSpdLimit"] = self.carrot_state.get("nRoadLimitSpeed", 0) + offset
                    self.carrot_state["xSpdDist"] = distance
                    self.carrot_state["xSpdType"] = xSpdType
                    self.log_navigation_message(f"🚨 Waze报告: {id_str}, 距离: {distance}m, 类型: {xSpdType}")

            # 处理其他KISA字段
            if "kisawazealert" in kisa_data:
                self.log_navigation_message(f"🚨 KISA警告: {kisa_data['kisawazealert']}")
            
            if "kisawazeendalert" in kisa_data:
                self.log_navigation_message(f"✅ KISA警告结束: {kisa_data['kisawazeendalert']}")

            # 更新GUI
            if hasattr(self, 'root'):
                self.root.after(0, self.update_data_display)

        except Exception as e:
            self.log_message(f"KISA processing error: {e}", "ERROR")

    def data_update_loop(self):
        """Continuous data update loop with CarrotMan state machine updates"""
        while self.is_running:
            try:
                # 更新CarrotMan状态机
                self.update_carrot_state()

                # 模拟车辆数据变化
                self.simulate_vehicle_movement()

                # 更新发动机转速基于速度和档位
                if self.vehicle_data["gear_shifter"] == "D" and self.vehicle_data["v_ego_kph"] > 0:
                    base_rpm = 800 + (self.vehicle_data["v_ego_kph"] * 30)
                    self.vehicle_data["engine_rpm"] = int(base_rpm + random.randint(-100, 100))
                else:
                    self.vehicle_data["engine_rpm"] = 800 + random.randint(-50, 50)

                # 更新巡航速度
                if self.vehicle_data["cruise_active"]:
                    self.vehicle_data["v_cruise_kph"] = max(30, self.vehicle_data["v_ego_kph"])
                else:
                    self.vehicle_data["v_cruise_kph"] = 0

                # 处理任何待处理的GUI更新
                if hasattr(self, 'pending_updates'):
                    self.process_pending_updates()

                # 模拟随机事件
                if random.random() < 0.01:  # 1% 概率每次更新
                    self.simulate_random_event()

                time.sleep(0.1)  # 10Hz 更新频率

            except Exception as e:
                self.log_message(f"Data update error: {e}", "ERROR")
                time.sleep(1)

    def simulate_vehicle_movement(self):
        """Simulate vehicle movement"""
        if self.vehicle_data["v_ego_kph"] > 0:
            # Simple GPS simulation - move slightly
            speed_ms = self.vehicle_data["v_ego_kph"] / 3.6
            heading_rad = math.radians(self.vehicle_data["heading"])

            # Calculate movement (very simplified)
            dt = 0.1  # 100ms
            distance = speed_ms * dt

            # Update position (rough approximation)
            lat_change = distance * math.cos(heading_rad) / 111000  # ~111km per degree
            lon_change = distance * math.sin(heading_rad) / (111000 * math.cos(math.radians(self.vehicle_data["latitude"])))

            self.vehicle_data["latitude"] += lat_change
            self.vehicle_data["longitude"] += lon_change

    def simulate_random_event(self):
        """模拟随机车辆事件 - 包含CarrotMan事件"""
        events = [
            ("traffic_light", lambda: self.set_traffic_state(random.randint(0, 3))),
            ("sdi_camera", lambda: self.set_sdi_event()),
            ("turn_signal", lambda: self.random_turn_signal()),
            ("speed_change", lambda: self.random_speed_change()),
            ("carrot_activation", lambda: self.random_carrot_activation()),
            ("navigation_update", lambda: self.random_navigation_update())
        ]

        event_name, event_func = random.choice(events)
        try:
            event_func()
            self.log_message(f"🎲 Random event: {event_name}")
        except Exception as e:
            self.log_message(f"Random event error: {e}", "ERROR")

    def random_carrot_activation(self):
        """随机CarrotMan激活事件"""
        if random.random() < 0.3:  # 30%概率激活
            self.carrot_state["active_count"] = random.randint(50, 100)
            self.carrot_state["active_sdi_count"] = random.randint(50, 200)
            self.log_navigation_message(f"🥕 CarrotMan激活: active_count={self.carrot_state['active_count']}, sdi_count={self.carrot_state['active_sdi_count']}")

    def random_navigation_update(self):
        """随机导航更新事件"""
        # 模拟转弯信息
        if random.random() < 0.2:  # 20%概率
            turn_types = [12, 13, 16, 19, 102, 101, 7, 6]  # 常见转弯类型
            self.carrot_state["nTBTTurnType"] = random.choice(turn_types)
            self.carrot_state["nTBTDist"] = random.randint(100, 1000)
            self.update_tbt_info()
            self.log_navigation_message(f"🔄 随机转弯: 类型={self.carrot_state['nTBTTurnType']}, 距离={self.carrot_state['nTBTDist']}m")

        # 模拟SDI信息
        if random.random() < 0.15:  # 15%概率
            sdi_types = [1, 2, 7, 8, 22]  # 常见SDI类型
            self.carrot_state["nSdiType"] = random.choice(sdi_types)
            self.carrot_state["nSdiSpeedLimit"] = random.randint(30, 80)
            self.carrot_state["nSdiDist"] = random.randint(200, 800)
            self.update_sdi_info()
            self.log_navigation_message(f"📷 随机SDI: 类型={self.carrot_state['nSdiType']}, 限速={self.carrot_state['nSdiSpeedLimit']}km/h, 距离={self.carrot_state['nSdiDist']}m")

    def set_traffic_state(self, state: int):
        """设置交通灯状态"""
        self.carrot_state["traffic_state"] = state
        self.vehicle_data["traffic_state"] = state
        states = {0: "None", 1: "Red", 2: "Green", 3: "Left Turn"}
        self.log_navigation_message(f"🚦 交通灯: {states.get(state, 'Unknown')}")

    def set_sdi_event(self):
        """模拟SDI摄像头事件"""
        sdi_types = [1, 2, 7, 8, 22]  # 常见SDI类型
        self.carrot_state["nSdiType"] = random.choice(sdi_types)
        self.carrot_state["nSdiSpeedLimit"] = random.randint(30, 80)
        self.carrot_state["nSdiDist"] = random.randint(200, 800)
        self.carrot_state["active_sdi_count"] = self.carrot_state["active_sdi_count_max"]
        self.update_sdi_info()
        self.log_navigation_message(f"📷 SDI事件: 类型={self.carrot_state['nSdiType']}, 限速={self.carrot_state['nSdiSpeedLimit']}km/h, 距离={self.carrot_state['nSdiDist']}m")

    def random_turn_signal(self):
        """Random turn signal activation"""
        if random.random() < 0.5:
            self.vehicle_data["left_blinker"] = not self.vehicle_data["left_blinker"]
            self.left_signal_var.set(self.vehicle_data["left_blinker"])
        else:
            self.vehicle_data["right_blinker"] = not self.vehicle_data["right_blinker"]
            self.right_signal_var.set(self.vehicle_data["right_blinker"])

    def random_speed_change(self):
        """Random speed change"""
        if self.vehicle_data["v_ego_kph"] > 0:
            change = random.randint(-10, 10)
            new_speed = max(0, min(120, self.vehicle_data["v_ego_kph"] + change))
            self.vehicle_data["v_ego_kph"] = new_speed
            self.speed_var.set(new_speed)

    def run(self):
        """Run the simulator"""
        try:
            self.log_message("🚗 Comma3 Device Simulator Ready")
            self.log_message(f"📍 Local IP: {self.local_ip}")
            self.log_message("Click 'Start Simulator' to begin")

            self.root.mainloop()

        except KeyboardInterrupt:
            self.log_message("Simulator interrupted by user")
        except Exception as e:
            self.log_message(f"Simulator error: {e}", "ERROR")
            traceback.print_exc()
        finally:
            self.stop_simulator()

    def __del__(self):
        """Cleanup on destruction"""
        if hasattr(self, 'is_running') and self.is_running:
            self.stop_simulator()


def main():
    """Main entry point"""
    print("Starting Comma3 Device Simulator...")
    print("=" * 50)

    try:
        simulator = Comma3Simulator()
        simulator.run()
    except Exception as e:
        print(f"❌ Failed to start simulator: {e}")
        traceback.print_exc()
        return 1

    return 0


if __name__ == "__main__":
    exit(main())
