package bi.two.algo;

//---------------------------------------------------------------
public class Node<T, X extends Node> {
    public T m_param;
    public X m_next;
    public X m_prev;

    public Node(X prev, T param, X next) {
        m_param = param;
        m_next = next;
        m_prev = prev;
    }
}
