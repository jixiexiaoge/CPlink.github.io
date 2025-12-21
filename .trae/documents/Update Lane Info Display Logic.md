I will modify `app/src/main/java/com/example/carrotamap/MainActivityUI.kt` to update the lane information display logic.

**Plan:**

1. **Update** **`LaneInfoDisplay`** **Composable**:

   * Add a new parameter `xiaogeData: XiaogeVehicleData? = null` to the function signature.

   * In the logic where `laneInfoList` is empty (around line 1862):

     * Replace the hardcoded "等待导航信息..." text with dynamic logic.

     * Check if `xiaogeData?.modelV2?.meta` is available.

     * If available, retrieve `laneWidthLeft` and `laneWidthRight`.

     * Apply the logic:

       * If `laneWidthLeft > 3.2` and `laneWidthRight > 3.2` -> Display "在中间车道行驶".

       * If `laneWidthLeft <= 3.2` (and implicitly `laneWidthRight > 3.2` or just prioritize left) -> Display "在最左侧车道行驶".

       * If `laneWidthRight <= 3.2` -> Display "在最右侧车道行驶".

       * (Handle case where both are <= 3.2, possibly displaying "车道行驶中" or similar).

     * If data is missing (meta is null), display "无视觉车道信息数据".

2. **Update** **`HomePage`** **Composable**:

   * Locate the call to `LaneInfoDisplay` (around line 251).

   * Retrieve the current `xiaogeData` state using `val data by core.xiaogeData`.

   * Pass `xiaogeData = data` to the `LaneInfoDisplay` call.

3. **Compile and Verify**:

   * Run `./gradlew assembleDebug` to ensure the changes compile correctly.

