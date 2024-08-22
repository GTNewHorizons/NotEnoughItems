package codechicken.nei.config;

import java.util.LinkedList;

import net.minecraft.util.RegistryNamespaced;

public abstract class RegistryDumper<T> extends DataDumper {

    public RegistryDumper(String name) {
        super(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<String[]> dump(int mode) {
        LinkedList<String[]> list = new LinkedList<>();
        RegistryNamespaced registry = registry();

        for (T obj : (Iterable<T>) registry)
            list.add(dump(obj, registry.getIDForObject(obj), registry.getNameForObject(obj)));

        return list;
    }

    public abstract RegistryNamespaced registry();

    public abstract String[] dump(T obj, int id, String name);

    @Override
    public int modeCount() {
        return 1;
    }
}
