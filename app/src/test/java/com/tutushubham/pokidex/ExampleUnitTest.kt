package com.tutushubham.pokidex

import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {


    class ListNode(var `val`: Int) {
        var next: ListNode? = null

        override fun toString(): String {
            return "ListNode(`val`=$`val`, next=$next)"
        }

    }

    data class Node<T : Any>(
        var value: T, var next: Node<T>? = null
    ) {


        fun printInReverse() {
            this.next?.printInReverse()

            if (next != null) {
                print(" -> ")
            }

            print(this.value.toString())

        }

        override fun toString(): String {
            println("called")
            return if (next != null) {
                "$value -> $next"
            } else {
                "$value"
            }
        }
    }

    fun printList(list: ListNode?) {
        var current = list
        while (current != null) {
            print("${current.`val`} -> ")
            current = current.next
        }
    }

    @Test
    fun test() {
        // tests:
        // list1 = [1,2,2,1]

        val list1 = Node(1)
        list1.next = Node(2)
        list1.next!!.next = Node(3)
        list1.next!!.next!!.next = Node(4)
        list1.next!!.next!!.next!!.next = Node(5)



        println(list1.toString())
        list1.printInReverse()


    }

    fun mergeTwoLists(list1: ListNode?, list2: ListNode?): ListNode? {

        var result = ListNode(0)
        result.next = null
        var temp = result

        var head1 = list1
        var head2 = list2

        while (head1 != null && head2 != null) {

            if (head1.`val` > head2.`val`) {
                temp.next = head2
                head2 = head2.next
            } else {
                temp.next = head1
                head1 = head1.next
            }
            temp = temp.next!!
        }

        if (head1 != null) {
            temp.next = head1
        }

        if (head2 != null) {
            temp.next = head2
        }


        return result.next
    }

    fun isPalindrome(head: ListNode?): Boolean {

        if (head?.next == null) return true
        if (head.next?.next == null) return head.`val` == head.next!!.`val`

        var slow = head
        var fast = head

        while (fast?.next != null && fast.next!!.next != null) {
            slow = slow?.next
            fast = fast.next?.next
        }

        var secondHalf = slow?.next

        slow?.next = null

        var prev: ListNode? = null
        var current = secondHalf
        var next: ListNode? = null

        while (current != null) {
            next = current.next
            current.next = prev
            prev = current
            current = next
        }

        var left = head
        var right = prev

        while (right != null) {
            if (left?.`val` != right.`val`) return false
            left = left.next
            right = right.next
        }

        return true

    }


}