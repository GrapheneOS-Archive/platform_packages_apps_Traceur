LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_CERTIFICATE := platform

LOCAL_PACKAGE_NAME := Traceur

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
