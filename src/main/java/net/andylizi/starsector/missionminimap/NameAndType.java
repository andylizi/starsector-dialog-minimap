package net.andylizi.starsector.missionminimap;

import org.objectweb.asm.Type;

public final class NameAndType {
    public final String name;
    public final Type type;

    public NameAndType(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return name + " " + type;
    }
}
