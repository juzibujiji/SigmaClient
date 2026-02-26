package com.mentalfrostbyte.jello.util.game.world.pathing;

import com.mentalfrostbyte.jello.util.system.math.vector.Vector3d;

import java.util.ArrayList;

public class Path {
    private static String[] field38374;
    private Vector3d field38375 = null;
    private Path field38376 = null;
    private ArrayList<Vector3d> field38377;
    private double field38378;
    private double field38379;
    private double field38380;

    public Path(Vector3d var1, Path var2, ArrayList<Vector3d> var3, double var4, double var6, double var8) {
        this.field38375 = var1;
        this.field38376 = var2;
        this.field38377 = var3;
        this.field38378 = var4;
        this.field38379 = var6;
        this.field38380 = var8;
    }

    public Vector3d method30354() {
        return this.field38375;
    }

    public Path method30355() {
        return this.field38376;
    }

    public ArrayList<Vector3d> method30356() {
        return this.field38377;
    }

    public double method30357() {
        return this.field38378;
    }

    public double method30358() {
        return this.field38379;
    }

    public void method30359(Vector3d var1) {
        this.field38375 = var1;
    }

    public void method30360(Path var1) {
        this.field38376 = var1;
    }

    public void method30361(ArrayList<Vector3d> var1) {
        this.field38377 = var1;
    }

    public void method30362(double var1) {
        this.field38378 = var1;
    }

    public void method30363(double var1) {
        this.field38379 = var1;
    }

    public double method30364() {
        return this.field38380;
    }

    public void method30365(double var1) {
        this.field38380 = var1;
    }
}
