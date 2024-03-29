/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.testing.IteratorFeature.MODIFIABLE;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import static java.util.Collections.emptyList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Unit test for IteratorTester.
 *
 * @author Mick Killianey
 */
@SuppressWarnings("serial") // No serialization is used in this test
public class IteratorTesterTest extends TestCase {

  public void testCanCatchDifferentLengthOfIteration() throws Exception {
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(4, MODIFIABLE, newArrayList(1, 2, 3),
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return Lists.newArrayList(1, 2, 3, 4).iterator();
          }
        };
    assertFailure(tester);
  }

  public void testCanCatchDifferentContents() throws Exception {
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(3, MODIFIABLE, newArrayList(1, 2, 3),
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return Lists.newArrayList(1, 3, 2).iterator();
          }
        };
    assertFailure(tester);
  }

  public void testCanCatchDifferentRemoveBehaviour() throws Exception {
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(3, MODIFIABLE, newArrayList(1, 2),
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return ImmutableList.of(1, 2).iterator();
          }
        };
    assertFailure(tester);
  }

  public void testUnknownOrder() throws Exception {
    new IteratorTester<Integer>(3, MODIFIABLE, newArrayList(1, 2),
        IteratorTester.KnownOrder.UNKNOWN_ORDER) {
      @Override protected Iterator<Integer> newTargetIterator() {
        return newArrayList(2, 1).iterator();
      }
    }.test();
  }

  public void testUnknownOrderUnrecognizedElement() throws Exception {
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(3, MODIFIABLE, newArrayList(1, 2, 50),
            IteratorTester.KnownOrder.UNKNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return newArrayList(2, 1, 3).iterator();
          }
        };
    assertFailure(tester);
  }

  /**
   * This Iterator wraps another iterator and gives it a bug found
   * in JDK6.
   *
   * <p>This bug is this: if you create an iterator from a TreeSet
   * and call next() on that iterator when hasNext() is false, so
   * that next() throws a NoSuchElementException, then subsequent
   * calls to remove() will incorrectly throw an IllegalStateException,
   * instead of removing the last element returned.
   *
   * <p>See
   * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6529795">
   * Sun bug 6529795</a>
   */
  static class IteratorWithSunJavaBug6529795<T> implements Iterator<T> {
    Iterator<T> iterator;
    boolean nextThrewException;
    IteratorWithSunJavaBug6529795(Iterator<T> iterator) {
      this.iterator = iterator;
    }

    public boolean hasNext() {
      return iterator.hasNext();
    }

    public T next() {
      try {
        return iterator.next();
      } catch (NoSuchElementException e) {
        nextThrewException = true;
        throw e;
      }
    }

    public void remove() {
      if (nextThrewException) {
        throw new IllegalStateException();
      }
      iterator.remove();
    }
  }

  public void testCanCatchSunJavaBug6529795InTargetIterator()
      throws Exception {
    try {
      /* Choose 4 steps to get sequence [next, next, next, remove] */
      new IteratorTester<Integer>(4, MODIFIABLE, newArrayList(1, 2),
          IteratorTester.KnownOrder.KNOWN_ORDER) {
        @Override protected Iterator<Integer> newTargetIterator() {
          Iterator<Integer> iterator = Lists.newArrayList(1, 2).iterator();
          return new IteratorWithSunJavaBug6529795<Integer>(iterator);
        }
      }.test();
    } catch (AssertionFailedError e) {
      return;
    }
    fail("Should have caught jdk6 bug in target iterator");
  }

  public void testCanWorkAroundSunJavaBug6529795InTargetIterator()
      throws Exception {
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(4, MODIFIABLE, newArrayList(1, 2),
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            Iterator<Integer> iterator = Lists.newArrayList(1, 2).iterator();
            return new IteratorWithSunJavaBug6529795<Integer>(iterator);
          }
        };

    /*
     * Calling this method on an IteratorTester should avoid flagging
     * the bug exposed by the preceding test.
     */
    tester.ignoreSunJavaBug6529795();

    tester.test();
  }

  private static final int STEPS = 3;
  static class TesterThatCountsCalls extends IteratorTester<Integer> {
    TesterThatCountsCalls() {
      super(STEPS, MODIFIABLE, newArrayList(1),
          IteratorTester.KnownOrder.KNOWN_ORDER);
    }
    int numCallsToNewTargetIterator;
    int numCallsToVerify;
    @Override protected Iterator<Integer> newTargetIterator() {
      numCallsToNewTargetIterator++;
      return Lists.newArrayList(1).iterator();
    }
    @Override protected void verify(List<Integer> elements) {
      numCallsToVerify++;
      super.verify(elements);
    }
  }

  public void testVerifyGetsCalled() throws Exception {
    TesterThatCountsCalls tester = new TesterThatCountsCalls();

    tester.test();

    assertEquals("Should have verified once per stimulus executed",
        tester.numCallsToVerify,
        tester.numCallsToNewTargetIterator * STEPS);
  }

  public void testVerifyCanThrowAssertionThatFailsTest() throws Exception {
    final String message = "Important info about why verify failed";
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(1, MODIFIABLE, newArrayList(1, 2, 3),
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return Lists.newArrayList(1, 2, 3).iterator();
          }

          @Override protected void verify(List<Integer> elements) {
            throw new AssertionFailedError(message);
          }
        };
    AssertionFailedError actual = null;
    try {
      tester.test();
    } catch (AssertionFailedError e) {
      actual = e;
    }
    assertNotNull("verify() should be able to cause test failure", actual);
    assertTrue("AssertionFailedError should have info about why test failed",
        actual.getCause().getMessage().contains(message));
  }

  public void testMissingException() throws Exception {
    List<Integer> emptyList = newArrayList();

    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(1, MODIFIABLE, emptyList,
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return new Iterator<Integer>() {
              public void remove() {
                // We should throw here, but we won't!
              }
              public Integer next() {
                // We should throw here, but we won't!
                return null;
              }
              public boolean hasNext() {
                return false;
              }
            };
          }
        };
    assertFailure(tester);
  }

  public void testUnexpectedException() throws Exception {
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(1, MODIFIABLE, newArrayList(1),
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return new ThrowingIterator<Integer>(new IllegalStateException());
          }
        };
    assertFailure(tester);
  }

  public void testSimilarException() throws Exception {
    List<Integer> emptyList = emptyList();
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(1, MODIFIABLE, emptyList,
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return new Iterator<Integer>() {
              public void remove() {
                throw new IllegalStateException() { /* subclass */};
              }
              public Integer next() {
                throw new NoSuchElementException() { /* subclass */};
              }
              public boolean hasNext() {
                return false;
              }
            };
          }
        };
    tester.test();
  }

  public void testMismatchedException() throws Exception {
    List<Integer> emptyList = emptyList();
    IteratorTester<Integer> tester =
        new IteratorTester<Integer>(1, MODIFIABLE, emptyList,
            IteratorTester.KnownOrder.KNOWN_ORDER) {
          @Override protected Iterator<Integer> newTargetIterator() {
            return new Iterator<Integer>() {
              public void remove() {
                // Wrong exception type.
                throw new IllegalArgumentException();
              }
              public Integer next() {
                // Wrong exception type.
                throw new UnsupportedOperationException();
              }
              public boolean hasNext() {
                return false;
              }
            };
          }
        };
    assertFailure(tester);
  }

  private static void assertFailure(IteratorTester<?> tester) throws Exception {
    try {
      tester.test();
      fail();
    } catch (AssertionFailedError expected) {
    }
  }

  private static final class ThrowingIterator<E> implements Iterator<E> {
    private final RuntimeException ex;

    private ThrowingIterator(RuntimeException ex) {
      this.ex = ex;
    }

    public boolean hasNext() {
      // IteratorTester doesn't expect exceptions for hasNext().
      return true;
    }

    public E next() {
      ex.fillInStackTrace();
      throw ex;
    }

    public void remove() {
      ex.fillInStackTrace();
      throw ex;
    }
  }
}
