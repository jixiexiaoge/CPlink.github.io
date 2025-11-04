#!/usr/bin/env python3
import numpy as np
import cereal.messaging as messaging
from typing import Optional, Union, Dict
from datetime import datetime
import time
import json
import io
import os
import requests
import shutil
from common.params import Params
from PIL import Image

params = Params()
SENSITIVITY_THRESHOLD = 0.08
TRIGGERED_TIME = 2


class SentryMode:

  def __init__(self):
    self.sm = messaging.SubMaster(['accelerometer'])
    self.curr_accel = 0
    self.prev_accel = None
    self.sentry_status = False
    self.secDelay = 0
    self.webhook_url = params.get("SentryDhook", encoding='utf8')
    self.transition_to_offroad_last = time.monotonic()
    self.offroad_delay = 90
    self.frontAllowed = params.get("RecordFront")

  def takeSnapshot(self) -> Optional[Dict[str, str]]:
    from openpilot.system.camerad.snapshot.snapshot import snapshot, jpeg_write
    pic, fpic = snapshot()
    if pic is not None:
      print(pic.shape)
      jpeg_write("back_image.jpg", pic)
    if fpic is not None:
      jpeg_write("front_image.jpg", fpic)
    if pic is not None and fpic is not None:
      self.stitch_images('front_image.jpg', 'back_image.jpg', '360_image.jpg')
    self.save_images()
    if pic is not None:
      return
    else:
      raise Exception("not available while camerad is started")

  def send_discord_webhook(self, webhook_url, message, image_path=None):
    data = {"content": message}
    if image_path:
        with open(image_path, "rb") as file:
            files = {"file": file}
            response = requests.post(webhook_url, data=data, files=files)
    else:
        headers = {"Content-Type": "application/json"}
        response = requests.post(webhook_url, json=data, headers=headers)
    if response.status_code == 200 or response.status_code == 204:
      print("Message sent successfully")
    else:
      print(f"Failed to send message. Status code: {response.status_code}")

  def stitch_images(self, front_image_path, back_image_path, output_path):
    front_image = Image.open(front_image_path)
    back_image = Image.open(back_image_path)
    front_width, front_height = front_image.size
    back_width, back_height = back_image.size
    if front_height != back_height:
        print("Error: Images must have the same height.")
        return
    result_image = Image.new("RGB", (front_width + back_width, front_height))
    result_image.paste(front_image, (0, 0))
    result_image.paste(back_image, (front_width, 0))
    result_image.save(output_path)

  def save_images(self):
    timestamp = datetime.now().strftime("%Y-%m-%d_%H:%M:%S")
    target_directory = f"/data/media/0/sentryd/"
    os.makedirs(target_directory, exist_ok=True)
    # Copy images to the new directory with new filenames
    if "back_image.jpg" is not None:
      shutil.copy("back_image.jpg", f"{target_directory}back_image_{timestamp}.jpg")
    if "front_image.jpg" is not None:
      shutil.copy("front_image.jpg", f"{target_directory}front_image_{timestamp}.jpg")
    if "ba360_imageck_image.jpg" is not None:
      image_path = f"{target_directory}360_image_{timestamp}.jpg"
      shutil.copy("360_image.jpg", image_path)
    message = 'ALERT! Sentry Detected Movement!'
    self.send_discord_webhook(self.webhook_url, message, image_path)

  def update(self):
    t = time.monotonic()
    if (t - self.transition_to_offroad_last) > self.offroad_delay:
      # Extract acceleration data
      self.curr_accel = np.array(self.sm['accelerometer'].acceleration.v)
      # Initialize
      if self.prev_accel is None:
        print("SentryD Active")
        self.prev_accel = self.curr_accel
      # Calculate magnitude change
      delta = abs(np.linalg.norm(self.curr_accel) - np.linalg.norm(self.prev_accel))
      # Trigger Check
      if delta > SENSITIVITY_THRESHOLD:
        self.last_timestamp = t
        self.secDelay += 1
        if self.secDelay % 150 == 0 and self.webhook_url is not None:
          self.sentry_status = True
          print("Triggered")
          self.secDelay = 0
          if self.frontAllowed:
            self.takeSnapshot()
          else:
            message = 'ALERT! Sentry Detected Movement!'
            self.send_discord_webhook(self.webhook_url, message)
      # Trigger Reset
      elif self.sentry_status and time.monotonic() - self.last_timestamp > TRIGGERED_TIME:
        self.sentry_status = False
        print("Movement Ended")
      self.prev_accel = self.curr_accel

  def start(self):
    while True:
      self.sm.update()
      self.update()

def main():
  sentry_mode = SentryMode()
  sentry_mode.start()

if __name__ == "__main__":
  main()
