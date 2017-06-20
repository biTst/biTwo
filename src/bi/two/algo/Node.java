package bi.two.algo;

//---------------------------------------------------------------
public class Node<T> {
    public T m_param;
    protected Node<T> m_next;
    protected Node<T> m_prev;

    public Node(Node<T> prev, T param, Node<T> next) {
        m_param = param;
        m_next = next;
        m_prev = prev;
    }
}
