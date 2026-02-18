package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;

/**
 * Request para POST /data/clean.
 */
public class CleanRequestDTO {

    @SerializedName("upload_id")
    private String uploadId;

    private CleaningOptionsDTO options;

    public CleanRequestDTO(String uploadId) {
        this.uploadId = uploadId;
        this.options = new CleaningOptionsDTO();
    }

    public String getUploadId() { return uploadId; }
    public CleaningOptionsDTO getOptions() { return options; }
    public void setOptions(CleaningOptionsDTO options) { this.options = options; }

    public static class CleaningOptionsDTO {
        @SerializedName("remove_duplicates")
        private boolean removeDuplicates = true;

        @SerializedName("handle_nulls")
        private boolean handleNulls = true;

        @SerializedName("null_strategy")
        private String nullStrategy = "fill_median";

        @SerializedName("detect_outliers")
        private boolean detectOutliers = true;

        @SerializedName("outlier_threshold")
        private double outlierThreshold = 3.0;

        @SerializedName("normalize_text")
        private boolean normalizeText = true;

        public void setNullStrategy(String nullStrategy) { this.nullStrategy = nullStrategy; }
        public void setRemoveDuplicates(boolean v) { this.removeDuplicates = v; }
        public void setHandleNulls(boolean v) { this.handleNulls = v; }
        public void setDetectOutliers(boolean v) { this.detectOutliers = v; }
    }
}
