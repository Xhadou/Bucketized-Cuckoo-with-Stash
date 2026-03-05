package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import cuckoo.hash.MurmurHash3;
import cuckoo.stats.BenchmarkStats;

public class ChainingHashTable<K, V> implements CuckooHashTable<K, V> {

    private static final int SEED = 42;
    private static final double LOAD_FACTOR_THRESHOLD = 0.75;

    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> next;

        Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    @SuppressWarnings("unchecked")
    private Node<K, V>[] table;
    private int capacity;
    private int size;
    private final BenchmarkStats stats = new BenchmarkStats();

    @SuppressWarnings("unchecked")
    public ChainingHashTable(int initialCapacity) {
        this.capacity = Math.max(initialCapacity, 1);
        this.table = new Node[this.capacity];
        this.size = 0;
    }

    private int hash(K key) {
        return Math.floorMod(MurmurHash3.hash32(key.hashCode(), SEED), capacity);
    }

    @Override
    public V get(K key) {
        int idx = hash(key);
        Node<K, V> node = table[idx];
        while (node != null) {
            if (node.key.equals(key)) {
                return node.value;
            }
            node = node.next;
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        int idx = hash(key);
        Node<K, V> node = table[idx];
        while (node != null) {
            if (node.key.equals(key)) {
                node.value = value;
                return;
            }
            node = node.next;
        }
        // Prepend new node
        table[idx] = new Node<>(key, value, table[idx]);
        size++;
        if ((double) size / capacity > LOAD_FACTOR_THRESHOLD) {
            resize(capacity * 2);
        }
    }

    @Override
    public V remove(K key) {
        int idx = hash(key);
        Node<K, V> prev = null;
        Node<K, V> node = table[idx];
        while (node != null) {
            if (node.key.equals(key)) {
                V val = node.value;
                if (prev == null) {
                    table[idx] = node.next;
                } else {
                    prev.next = node.next;
                }
                size--;
                return val;
            }
            prev = node;
            node = node.next;
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public double loadFactor() {
        return (double) size / capacity;
    }

    @Override
    public BenchmarkStats getStats() {
        return stats;
    }

    @SuppressWarnings("unchecked")
    private void resize(int newCapacity) {
        Node<K, V>[] oldTable = table;
        this.capacity = newCapacity;
        this.table = new Node[newCapacity];
        this.size = 0;
        for (Node<K, V> head : oldTable) {
            Node<K, V> node = head;
            while (node != null) {
                put(node.key, node.value);
                node = node.next;
            }
        }
    }
}
