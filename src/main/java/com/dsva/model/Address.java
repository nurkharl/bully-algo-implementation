package com.dsva.model;

public record Address(String hostname, int port, int nodeId) {
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || object.getClass() != object.getClass()) {
            return false;
        }

        Address address = (Address) object;
        return address.port == this.port && address.hostname.equals(this.hostname) && address.nodeId == this.nodeId;
    }
}