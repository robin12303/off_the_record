package dev.backend.dto;

public record HeartBeat(
        String machineGuid,
        String hostName,
        String cpuName,
        String gpuName,
        String ramTotalMb,
        String osName,
        String osVersion

) {
}
