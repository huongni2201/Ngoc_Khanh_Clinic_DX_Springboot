package com.ngockhanh.clinic.identity.api.http;

import java.net.InetAddress;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

public final class TrustedProxyClientIpResolver {

    private final List<String> trustedProxies;

    public TrustedProxyClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = List.copyOf(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!trustedProxies.contains(peer)) {
            return peer;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 1024) {
            return peer;
        }

        String[] chain = forwarded.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            try {
                peer = InetAddress.ofLiteral(chain[index].strip()).getHostAddress();
            } catch (IllegalArgumentException invalidAddress) {
                return request.getRemoteAddr();
            }
            if (!trustedProxies.contains(peer)) {
                return peer;
            }
        }
        return peer;
    }
}
