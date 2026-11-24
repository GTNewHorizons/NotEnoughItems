package codechicken.nei;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import codechicken.nei.api.ItemFilter;

public class ItemCategories implements ItemFilter.ItemFilterProvider {

    public static final class Category {

        public final String id;
        public final String nameKey;
        public final ItemFilter filter;
        public final boolean hiddenByDefault;

        Category(String id, String nameKey, ItemFilter filter, boolean hiddenByDefault) {
            this.id = id;
            this.nameKey = nameKey;
            this.filter = filter;
            this.hiddenByDefault = hiddenByDefault;
        }
    }

    private ItemCategories() {}

    private static final String KEY_HIDDEN = "inventory.categories.hidden";
    private static final String KEY_SHOWN = "inventory.categories.shown";

    private static volatile Set<String> hidden;
    private static volatile Set<String> shown;

    public static final ItemCategories INSTANCE = new ItemCategories();
    private static final List<Category> categories = new CopyOnWriteArrayList<>();

    public static Category register(String id, String nameKey, ItemFilter filter, boolean hiddenByDefault) {
        for (Category category : categories) {
            if (category.id.equals(id)) {
                throw new IllegalArgumentException("Duplicate item category ID " + id);
            }

            if (category.nameKey.equals(nameKey)) {
                throw new IllegalArgumentException("Duplicate item category label " + nameKey);
            }
        }

        Category category = new Category(id, nameKey, filter, hiddenByDefault);
        categories.add(category);
        ItemList.updateFilter.restart();
        return category;
    }

    private static void ensureLoaded() {
        if (hidden != null) {
            return;
        }
        synchronized (ItemCategories.class) {
            if (hidden == null) {
                shown = Collections.unmodifiableSet(readSet(KEY_SHOWN));
                hidden = Collections.unmodifiableSet(readSet(KEY_HIDDEN));
            }
        }
    }

    public static List<Category> all() {
        return Collections.unmodifiableList(categories);
    }

    public static boolean isEmpty() {
        return categories.isEmpty();
    }

    public static boolean isHidden(Category c) {
        ensureLoaded();
        if (hidden.contains(c.id)) return true;
        if (shown.contains(c.id)) return false;
        return c.hiddenByDefault;
    }

    public static boolean anyHidden() {
        for (Category category : categories) {
            if (isHidden(category)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void setHidden(Category c, boolean hide) {
        ensureLoaded();
        Set<String> h = new LinkedHashSet<>(hidden);
        Set<String> s = new LinkedHashSet<>(shown);
        if (hide) {
            h.add(c.id);
            s.remove(c.id);
        } else {
            h.remove(c.id);
            s.add(c.id);
        }
        hidden = Collections.unmodifiableSet(h);
        shown = Collections.unmodifiableSet(s);
        writeSet(KEY_HIDDEN, hidden);
        writeSet(KEY_SHOWN, shown);
        ItemList.updateFilter.restart();
    }

    @Override
    public ItemFilter getFilter() {
        ensureLoaded();
        final List<ItemFilter> hidden = new ArrayList<>();
        for (Category category : categories) {
            if (isHidden(category)) {
                hidden.add(category.filter);
            }
        }

        if (hidden.isEmpty()) {
            return item -> true;
        }

        return item -> {
            for (ItemFilter filter : hidden) {
                try {
                    if (filter.matches(item)) return false;
                } catch (Throwable t) {
                    // In case of an error, do nothing, so the item list doesn't break
                }
            }
            return true;
        };
    }

    private static Set<String> readSet(String key) {
        String raw = NEIClientConfig.getSetting(key).getValue("");
        Set<String> out = new LinkedHashSet<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            out.add(trimmed);
        }

        return out;
    }

    private static void writeSet(String key, Set<String> set) {
        NEIClientConfig.getSetting(key).setValue(String.join(",", set));
    }
}
