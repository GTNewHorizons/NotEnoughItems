package codechicken.nei.guihook;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A hideous LinkedList that is backed by an arbitrary list, for alternative characteristics while preserving binary
 * compatibility...
 * 
 * @param <E>
 */
final class HideousLinkedList<E> extends LinkedList<E> {

    private static final long serialVersionUID = -8504433551965776915L;
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
    public void addFirst(E e) {
        backing.add(0, e);
    }

    @Override
    public E getFirst() {
        if (backing.size() == 0) throw new NoSuchElementException();
        return (backing.get(0));
    }

    @Override
    public E removeFirst() {
        E item = getFirst();
        backing.remove(0);
        return item;
    }

    @Override
    public void addLast(E e) {
        backing.add(backing.size(), e);
    }

    @Override
    public E getLast() {
        if (backing.size() == 0) throw new NoSuchElementException();
        return backing.get(backing.size() - 1);
    }

    @Override
    public E removeLast() {
        E item = getLast();
        backing.remove(backing.size() - 1);
        return item;
    }

    @Override
    public E peek() {
        return backing.size() > 0 ? backing.get(0) : null;
    }

    @Override
    public E peekFirst() {
        return backing.size() > 0 ? getFirst() : null;
    }

    @Override
    public E peekLast() {
        return backing.size() > 0 ? getLast() : null;
    }

    @Override
    public E poll() {
        return backing.size() > 0 ? removeFirst() : null;
    }

    @Override
    public E pollFirst() {
        return poll();
    }

    @Override
    public E pollLast() {
        return backing.size() > 0 ? removeLast() : null;
    }

    @Override
    public void push(E e) {
        addFirst(e);
    }

    @Override
    public E pop() {
        return removeFirst();
    }

    @Override
    public boolean removeFirstOccurrence(Object e) {
        return remove(e);
    }

    @Override
    public boolean removeLastOccurrence(Object e) {
        int idx = this.backing.lastIndexOf(e);
        if (idx != -1) {
            this.backing.remove(idx);
            return true;
        }
        return false;
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
