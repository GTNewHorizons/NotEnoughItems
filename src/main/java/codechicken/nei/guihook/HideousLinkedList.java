package codechicken.nei.guihook;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A hideous LinkedList that is backed by an arbitrary list, for alternative characteristics while preserving
 * binary compatibility...
 * @param <E>
 */
final class HideousLinkedList<E> extends LinkedList<E> {
    private final List<E> backing;

    HideousLinkedList(List<E> backing) {
        this.backing = backing;
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return backing.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return backing.iterator();
    }

    @Override
    public Object[] toArray() {
        return backing.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return backing.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return backing.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return backing.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return backing.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return backing.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return backing.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return backing.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return backing.retainAll(c);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        backing.replaceAll(operator);
    }

    @Override
    public void sort(Comparator<? super E> c) {
        backing.sort(c);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public boolean equals(Object o) {
        return backing.equals(o);
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public E get(int index) {
        return backing.get(index);
    }

    @Override
    public E set(int index, E element) {
        return backing.set(index, element);
    }

    @Override
    public void add(int index, E element) {
        backing.add(index, element);
    }

    @Override
    public E remove(int index) {
        return backing.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return backing.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return backing.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return backing.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return backing.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return backing.subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<E> spliterator() {
        return backing.spliterator();
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return backing.removeIf(filter);
    }

    @Override
    public Stream<E> stream() {
        return backing.stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return backing.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        backing.forEach(action);
    }
}
