package com.numera.factory;

/** Pure grid geometry for joining the 12 perimeter sockets of a 3x3 miner. */
final class ConveyorJunctions {
    // Direction encoding matches FactoryView: east=0, south=1, west=2, north=3.
    static int incomingFromMine(int dx,int dy) {
        if(Math.abs(dx)==2&&Math.abs(dy)<=1)return dx>0?0:2;
        if(Math.abs(dy)==2&&Math.abs(dx)<=1)return dy>0?1:3;
        return -1;
    }
    private ConveyorJunctions(){}
}
