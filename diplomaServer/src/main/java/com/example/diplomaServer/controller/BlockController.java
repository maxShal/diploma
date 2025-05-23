package com.example.diplomaServer.controller;

import com.example.diplomaServer.dto.BlockRequest;
import com.example.diplomaServer.model.BlockedApp;
import com.example.diplomaServer.repository.BlockedAppRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/block")
public class BlockController {

    @Autowired
    private BlockedAppRepository repository;

    @PostMapping
    @Transactional
    public String handleBlockRequest(@RequestBody BlockRequest request) {
        if (request.isBlock()) {
            BlockedApp blockedApp = new BlockedApp();
            blockedApp.setDeviceId(request.getDeviceId());
            blockedApp.setPackageName(request.getPackageName());
            repository.save(blockedApp);
            return "App " + request.getPackageName() + " blocked for device " + request.getDeviceId();
        } else {
            repository.deleteByDeviceIdAndPackageName(request.getDeviceId(), request.getPackageName());
            return "App " + request.getPackageName() + " unblocked for device " + request.getDeviceId();
        }
    }

    @GetMapping("/{deviceId}")
    public List<String> getBlockedApps(@PathVariable String deviceId) {
        return repository.findByDeviceId(deviceId)
                .stream()
                .map(BlockedApp::getPackageName)
                .collect(Collectors.toList());
    }
}

