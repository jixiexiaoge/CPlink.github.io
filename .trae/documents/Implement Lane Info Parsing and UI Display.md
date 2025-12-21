I will implement the parsing and display of lane information (KEY_TYPE = 13012) as requested.

### 1. Data Model Update (`CarrotManDataModels.kt`)
- Add a `LaneInfo` data class to store individual lane details:
  - `id`: String (The icon ID)
  - `isRecommended`: Boolean (Whether this lane is advised)
- Add `laneInfoList: List<LaneInfo>` to `CarrotManFields` to hold the current lane state.

### 2. Broadcast Processing Logic (`AmapBroadcastHandlers.kt`)
- Update `handleDriveWayInfo`:
  - Parse the `drive_way_info` JSON array.
  - Extract `drive_way_lane_Back_icon` and `trafficLaneAdvised` for each lane.
  - Update `carrotManFields.laneInfoList` with the parsed data.
  - Clear the list when lane info is disabled or empty.

### 3. UI Implementation (`MainActivityUI.kt` & new helper)
- Create a `LaneIconHelper` object/function to map API IDs to Drawable Resources:
  - Implement the fallback logic: `global_image_landback_{dec}` -> `navistate_landback_{hex}` -> `navistate_landfront_{hex}` -> `navistate_auto_landback_{hex}`.
- Create a `LaneInfoDisplay` Composable in `MainActivityUI.kt`:
  - Position it at the very top of the `HomePage` content.
  - Display lanes horizontally.
  - Use opacity to distinguish recommended lanes (1.0 alpha) from others (0.5 alpha).
  - Fallback to displaying the raw ID text if no image resource is found.

### 4. Verification
- Verify the code compiles.
- Ensure the logic covers the specific ID mapping rules (Decimal vs Hex) observed in the file system.
