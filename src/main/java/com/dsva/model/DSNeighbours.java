package com.dsva.model;

public record DSNeighbours(Address next, Address nnext, Address prev, Address leader) {
    @Override
    public String toString() {
        return("Neigh[next:'"+next+"', " +
                "nnext:'"+nnext+"', " +
                "prev:'"+prev+"', " +
                "leader:'"+leader+"']");
    }
}