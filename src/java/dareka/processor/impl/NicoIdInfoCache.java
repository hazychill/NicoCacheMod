package dareka.processor.impl;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import dareka.common.Logger;

/**
 * Remember type(sm/ax/ca, etc.), title of movies. The size is shrunk
 * automatically when the rest of the free memory is low.
 *
 * <p>
 * This class has responsibility similar to WeakHashMap. However WeakHashMap is
 * not appropriate for cache because it release all entries when the GC works.
 *
 */
public class NicoIdInfoCache {
    private static final NicoIdInfoCache SINGLETON_INSTANCE =
            new NicoIdInfoCache();
    private static final int MAX_RECENT = 10000;

    private ReferenceQueue<Entry> queue = new ReferenceQueue<Entry>();
    private ConcurrentHashMap<String, EntryReference> id2title =
            new ConcurrentHashMap<String, EntryReference>();
    // final is necessary to prevent 2 threads from entering
    // synchronized block when the reference is changed on feature.
    private final LinkedList<Entry> recentEntry = new LinkedList<Entry>();

    public static NicoIdInfoCache getInstance() {
        return SINGLETON_INSTANCE;
    }

    /**
     * Get information of id.
     *
     * @param id the number of the movie. (sm/ax/ca is not included)
     * @return information
     */
    public Entry get(String id) {
        expunge();

        EntryReference entryRef = id2title.get(id);
        if (entryRef == null) {
            return null;
        }

        Entry entry = entryRef.get();
        if (entry == null) {
            return null;
        }

        return entry;
    }

    /**
     * Put information of id.
     *
     * @param type sm/ax/ca, etd.
     * @param id the number of the movie. (sm/ax/ca is not included)
     * @param title
     */
    public void put(String type, String id, String title) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (title == null) {
            throw new IllegalArgumentException("title must not be null");
        }


        Entry entry = new Entry(type, id, title);
        constructReference(id, entry);
    }

    /**
     * Put incomplete information of id without the title.
     * If there already is complete information, this method does nothing.
     *
     * @param type sm/ax/ca, etd.
     * @param id the number of the movie. (sm/ax/ca is not included)
     */
    public void putOnlyTypeAndId(String type, String id) {
        Entry existingEntry = get(id);
        if (existingEntry != null && existingEntry.isTitleValid()) {
            return;
        }

        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }

        Entry entry = new Entry(type, id);
        constructReference(id, entry);
    }

    private void constructReference(String id, Entry entry) {
        expunge();

        EntryReference entryRef = new EntryReference(entry, queue);
        id2title.put(id, entryRef);

        // keep strong reference for recent entries to protect them from GC.
        synchronized (recentEntry) {
            if (!recentEntry.remove(entry)) {
                if (recentEntry.size() >= MAX_RECENT) {
                    recentEntry.poll();
                }
            }

            recentEntry.add(entry);
        }
    }

    public int size() {
        expunge();

        return id2title.size();
    }

    private void expunge() {
        EntryReference ref;
        while ((ref = (EntryReference) queue.poll()) != null) {
            String id = ref.getId();
            id2title.remove(id);
            Logger.debugWithThread("title cache expunged: " + id);
        }
    }

    public static class Entry {
        // This String object represents that title is not found.
        // Constants may be shared with other String objects,
        // so we need a new String object.
        // Additionally, this is a kind of the null object pattern.
        private final static String INVALID_TITLE = new String("nicocache-unknown-title");

        private String type;
        private String id;
        private String title;

        Entry(String type, String id, String title) {
            this.type = type;
            this.id = id;
            this.title = title;
        }

        Entry(String type, String id) {
            this.type = type;
            this.id = id;
            this.title = INVALID_TITLE;
        }

        /* (”ñ Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Entry) {
                Entry other = (Entry) obj;
                return type.equals(other.type) && id.equals(other.id)
                        && title.equals(other.title);
            } else {
                return false;
            }
        }

        /* (”ñ Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return type.hashCode() ^ id.hashCode() ^ title.hashCode();
        }

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public boolean isTitleValid() {
            // intended String comparison. this require identity, not equality.
            if (title == INVALID_TITLE) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * Soft reference to the information. This make it possible for GC
     * collects the Entry object on low free memory.
     *
     */
    static class EntryReference extends SoftReference<Entry> {
        private String id;

        EntryReference(Entry entry, ReferenceQueue<Entry> q) {
            super(entry, q);
            this.id = entry.getId();
        }

        String getId() {
            return id;
        }

    }

}
