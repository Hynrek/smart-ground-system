package ch.jp.shooting.node.box;

public record BoxDiscoveryRequest(
        String macAddress,
        String token,
        String appVersion,
        String firmwareVersion,
        String boxType,
        String capabilitiesJson) {
}
