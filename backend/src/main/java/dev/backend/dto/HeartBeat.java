package dev.backend.dto;

public record HeartBeat(
        String machineUuid,
        String hostName,
        String cpuName,
        String gpuName,
        String ramTotalMb,
        String osName,
        String osVersion

) {
}
