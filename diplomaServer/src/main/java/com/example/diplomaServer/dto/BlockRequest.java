package com.example.diplomaServer.dto;

import java.util.Objects;

public class BlockRequest {
    private String deviceId;
    private String packageName;
    private boolean block;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isBlock() {
        return block;
    }

    public void setBlock(boolean block) {
        this.block = block;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BlockRequest that = (BlockRequest) o;
        return block == that.block && Objects.equals(deviceId, that.deviceId) && Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, packageName, block);
    }
}
