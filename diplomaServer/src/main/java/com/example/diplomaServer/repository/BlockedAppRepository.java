package com.example.diplomaServer.repository;

import com.example.diplomaServer.model.BlockedApp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BlockedAppRepository extends JpaRepository<BlockedApp, Long> {
    List<BlockedApp> findByDeviceId(String deviceId);
    void deleteByDeviceIdAndPackageName(String deviceId, String packageName);
}