package net.andylizi.starsector.missionminimap;

public final class DummyMain {
    public static void main(String[] args) throws Throwable {
        System.out.println("This program is meant to be used as a Java agent.");
    }

    private DummyMain() {
        throw new AssertionError();
    }
}
