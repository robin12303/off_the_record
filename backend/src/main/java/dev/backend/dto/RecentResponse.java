package dev.backend.dto;

import dev.backend.entity.Agent;
import java.time.LocalDateTime;

public record RecentResponse(
        Long id,
        String machineGuid,
        String ipAddress,
        String hostName,
        String cpuName,
        String gpuName,
        String ramTotalMb,
        String osName,
        String osVersion,
        LocalDateTime lastSeenAt

) {
    public static RecentResponse from(Agent b) {
        return new RecentResponse(
                b.getId(),
                b.getMachineGuid(),
                b.getIpAddress(),
                b.getHostName(),
                b.getCpuName(),
                b.getGpuName(),
                b.getRamTotalMb(),
                b.getOsName(),
                b.getOsVersion(),
                b.getLastSeenAt()
        );
    }
}
