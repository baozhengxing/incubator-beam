/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.transforms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.apache.beam.sdk.transforms.DoFn.Context;
import org.apache.beam.sdk.transforms.DoFn.ExtraContextFactory;
import org.apache.beam.sdk.transforms.DoFn.ProcessContext;
import org.apache.beam.sdk.transforms.DoFn.ProcessElement;
import org.apache.beam.sdk.transforms.DoFn.Setup;
import org.apache.beam.sdk.transforms.DoFn.Teardown;
import org.apache.beam.sdk.transforms.dofnreflector.DoFnReflectorTestHelper;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.util.UserCodeException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

/**
 * Tests for {@link DoFnReflector}.
 */
@RunWith(JUnit4.class)
public class DoFnReflectorTest {

  /**
   * A convenience struct holding flags that indicate whether a particular method was invoked.
   */
  public static class Invocations {
    public boolean wasProcessElementInvoked = false;
    public boolean wasStartBundleInvoked = false;
    public boolean wasFinishBundleInvoked = false;
    public boolean wasSetupInvoked = false;
    public boolean wasTeardownInvoked = false;
    private final String name;

    public Invocations(String name) {
      this.name = name;
    }
  }

  private DoFn<String, String> fn;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private DoFn<String, String>.ProcessContext mockContext;
  @Mock
  private BoundedWindow mockWindow;
  @Mock
  private DoFn.InputProvider<String> mockInputProvider;
  @Mock
  private DoFn.OutputReceiver<String> mockOutputReceiver;

  private ExtraContextFactory<String, String> extraContextFactory;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    this.extraContextFactory = new ExtraContextFactory<String, String>() {
      @Override
      public BoundedWindow window() {
        return mockWindow;
      }

      @Override
      public DoFn.InputProvider<String> inputProvider() {
        return mockInputProvider;
      }

      @Override
      public DoFn.OutputReceiver<String> outputReceiver() {
        return mockOutputReceiver;
      }
    };
  }

  private DoFnReflector underTest(DoFn<String, String> fn) {
    this.fn = fn;
    return DoFnReflector.of(fn.getClass());
  }

  private void checkInvokeProcessElementWorks(
      DoFnReflector r, Invocations... invocations) throws Exception {
    assertTrue("Need at least one invocation to check", invocations.length >= 1);
    for (Invocations invocation : invocations) {
      assertFalse("Should not yet have called processElement on " + invocation.name,
          invocation.wasProcessElementInvoked);
    }
    r.bindInvoker(fn).invokeProcessElement(mockContext, extraContextFactory);
    for (Invocations invocation : invocations) {
      assertTrue("Should have called processElement on " + invocation.name,
          invocation.wasProcessElementInvoked);
    }
  }

  private void checkInvokeStartBundleWorks(
      DoFnReflector r, Invocations... invocations) throws Exception {
    assertTrue("Need at least one invocation to check", invocations.length >= 1);
    for (Invocations invocation : invocations) {
      assertFalse("Should not yet have called startBundle on " + invocation.name,
          invocation.wasStartBundleInvoked);
    }
    r.bindInvoker(fn).invokeStartBundle(mockContext, extraContextFactory);
    for (Invocations invocation : invocations) {
      assertTrue("Should have called startBundle on " + invocation.name,
          invocation.wasStartBundleInvoked);
    }
  }

  private void checkInvokeFinishBundleWorks(
      DoFnReflector r, Invocations... invocations) throws Exception {
    assertTrue("Need at least one invocation to check", invocations.length >= 1);
    for (Invocations invocation : invocations) {
      assertFalse("Should not yet have called finishBundle on " + invocation.name,
          invocation.wasFinishBundleInvoked);
    }
    r.bindInvoker(fn).invokeFinishBundle(mockContext, extraContextFactory);
    for (Invocations invocation : invocations) {
      assertTrue("Should have called finishBundle on " + invocation.name,
          invocation.wasFinishBundleInvoked);
    }
  }

  private void checkInvokeSetupWorks(DoFnReflector r, Invocations... invocations) throws Exception {
    assertTrue("Need at least one invocation to check", invocations.length >= 1);
    for (Invocations invocation : invocations) {
      assertFalse("Should not yet have called setup on " + invocation.name,
          invocation.wasSetupInvoked);
    }
    r.bindInvoker(fn).invokeSetup();
    for (Invocations invocation : invocations) {
      assertTrue("Should have called setup on " + invocation.name,
          invocation.wasSetupInvoked);
    }
  }

  private void checkInvokeTeardownWorks(DoFnReflector r, Invocations... invocations)
      throws Exception {
    assertTrue("Need at least one invocation to check", invocations.length >= 1);
    for (Invocations invocation : invocations) {
      assertFalse("Should not yet have called teardown on " + invocation.name,
          invocation.wasTeardownInvoked);
    }
    r.bindInvoker(fn).invokeTeardown();
    for (Invocations invocation : invocations) {
      assertTrue("Should have called teardown on " + invocation.name,
          invocation.wasTeardownInvoked);
    }
  }

  @Test
  public void testDoFnWithNoExtraContext() throws Exception {
    final Invocations invocations = new Invocations("AnonymousClass");
    DoFnReflector reflector = underTest(new DoFn<String, String>() {

      @ProcessElement
      public void processElement(ProcessContext c)
          throws Exception {
        invocations.wasProcessElementInvoked = true;
        assertSame(c, mockContext);
      }
    });

    assertFalse(reflector.usesSingleWindow());

    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testDoFnInvokersReused() throws Exception {
    // Ensures that we don't create a new Invoker class for every instance of the OldDoFn.
    IdentityParent fn1 = new IdentityParent();
    IdentityParent fn2 = new IdentityParent();
    DoFnReflector reflector1 = underTest(fn1);
    DoFnReflector reflector2 = underTest(fn2);
    assertSame("DoFnReflector instances should be cached and reused for identical types",
        reflector1, reflector2);
    assertSame("Invoker classes should only be generated once for each type",
        reflector1.bindInvoker(fn1).getClass(),
        reflector2.bindInvoker(fn2).getClass());
  }

  interface InterfaceWithProcessElement {
    @ProcessElement
    void processElement(DoFn<String, String>.ProcessContext c);
  }

  interface LayersOfInterfaces extends InterfaceWithProcessElement {}

  private class IdentityUsingInterfaceWithProcessElement
      extends DoFn<String, String>
      implements LayersOfInterfaces {

    private Invocations invocations = new Invocations("Named Class");

    @Override
    public void processElement(DoFn<String, String>.ProcessContext c) {
      invocations.wasProcessElementInvoked = true;
      assertSame(c, mockContext);
    }
  }

  @Test
  public void testDoFnWithProcessElementInterface() throws Exception {
    IdentityUsingInterfaceWithProcessElement fn = new IdentityUsingInterfaceWithProcessElement();
    DoFnReflector reflector = underTest(fn);
    assertFalse(reflector.usesSingleWindow());
    checkInvokeProcessElementWorks(reflector, fn.invocations);
  }

  private class IdentityParent extends DoFn<String, String> {
    protected Invocations parentInvocations = new Invocations("IdentityParent");

    @ProcessElement
    public void process(ProcessContext c) {
      parentInvocations.wasProcessElementInvoked = true;
      assertSame(c, mockContext);
    }
  }

  private class IdentityChildWithoutOverride extends IdentityParent {
  }

  private class IdentityChildWithOverride extends IdentityParent {
    protected Invocations childInvocations = new Invocations("IdentityChildWithOverride");

    @Override
    public void process(DoFn<String, String>.ProcessContext c) {
      super.process(c);
      childInvocations.wasProcessElementInvoked = true;
    }
  }

  @Test
  public void testDoFnWithMethodInSuperclass() throws Exception {
    IdentityChildWithoutOverride fn = new IdentityChildWithoutOverride();
    DoFnReflector reflector = underTest(fn);
    assertFalse(reflector.usesSingleWindow());
    checkInvokeProcessElementWorks(reflector, fn.parentInvocations);
  }

  @Test
  public void testDoFnWithMethodInSubclass() throws Exception {
    IdentityChildWithOverride fn = new IdentityChildWithOverride();
    DoFnReflector reflector = underTest(fn);
    assertFalse(reflector.usesSingleWindow());
    checkInvokeProcessElementWorks(reflector, fn.parentInvocations, fn.childInvocations);
  }

  @Test
  public void testDoFnWithWindow() throws Exception {
    final Invocations invocations = new Invocations("AnonymousClass");
    DoFnReflector reflector = underTest(new DoFn<String, String>() {

      @ProcessElement
      public void processElement(ProcessContext c, BoundedWindow w)
          throws Exception {
        invocations.wasProcessElementInvoked = true;
        assertSame(c, mockContext);
        assertSame(w, mockWindow);
      }
    });

    assertTrue(reflector.usesSingleWindow());

    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testDoFnWithOutputReceiver() throws Exception {
    final Invocations invocations = new Invocations("AnonymousClass");
    DoFnReflector reflector = underTest(new DoFn<String, String>() {

      @ProcessElement
      public void processElement(ProcessContext c, DoFn.OutputReceiver<String> o)
          throws Exception {
        invocations.wasProcessElementInvoked = true;
        assertSame(c, mockContext);
        assertSame(o, mockOutputReceiver);
      }
    });

    assertFalse(reflector.usesSingleWindow());

    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testDoFnWithInputProvider() throws Exception {
    final Invocations invocations = new Invocations("AnonymousClass");
    DoFnReflector reflector = underTest(new DoFn<String, String>() {

      @ProcessElement
      public void processElement(ProcessContext c, DoFn.InputProvider<String> i)
          throws Exception {
        invocations.wasProcessElementInvoked = true;
        assertSame(c, mockContext);
        assertSame(i, mockInputProvider);
      }
    });

    assertFalse(reflector.usesSingleWindow());

    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testDoFnWithStartBundle() throws Exception {
    final Invocations invocations = new Invocations("AnonymousClass");
    DoFnReflector reflector = underTest(new DoFn<String, String>() {
      @ProcessElement
      public void processElement(@SuppressWarnings("unused") ProcessContext c) {}

      @StartBundle
      public void startBundle(Context c) {
        invocations.wasStartBundleInvoked = true;
        assertSame(c, mockContext);
      }

      @FinishBundle
      public void finishBundle(Context c) {
        invocations.wasFinishBundleInvoked = true;
        assertSame(c, mockContext);
      }
    });

    checkInvokeStartBundleWorks(reflector, invocations);
    checkInvokeFinishBundleWorks(reflector, invocations);
  }

  @Test
  public void testDoFnWithSetupTeardown() throws Exception {
    final Invocations invocations = new Invocations("AnonymousClass");
    DoFnReflector reflector = underTest(new DoFn<String, String>() {
      @ProcessElement
      public void processElement(@SuppressWarnings("unused") ProcessContext c) {}

      @StartBundle
      public void startBundle(Context c) {
        invocations.wasStartBundleInvoked = true;
        assertSame(c, mockContext);
      }

      @FinishBundle
      public void finishBundle(Context c) {
        invocations.wasFinishBundleInvoked = true;
        assertSame(c, mockContext);
      }

      @Setup
      public void before() {
        invocations.wasSetupInvoked = true;
      }

      @Teardown
      public void after() {
        invocations.wasTeardownInvoked = true;
      }
    });

    checkInvokeSetupWorks(reflector, invocations);
    checkInvokeTeardownWorks(reflector, invocations);
  }

  @Test
  public void testNoProcessElement() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("No method annotated with @ProcessElement found");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {});
  }

  @Test
  public void testMultipleProcessElement() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Found multiple methods annotated with @ProcessElement");
    thrown.expectMessage("foo()");
    thrown.expectMessage("bar()");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {
      @ProcessElement
      public void foo() {}

      @ProcessElement
      public void bar() {}
    });
  }

  @Test
  public void testMultipleStartBundleElement() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Found multiple methods annotated with @StartBundle");
    thrown.expectMessage("bar()");
    thrown.expectMessage("baz()");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {
      @ProcessElement
      public void foo() {}

      @StartBundle
      public void bar() {}

      @StartBundle
      public void baz() {}
    });
  }

  @Test
  public void testMultipleFinishBundleElement() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Found multiple methods annotated with @FinishBundle");
    thrown.expectMessage("bar()");
    thrown.expectMessage("baz()");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {
      @ProcessElement
      public void foo() {}

      @FinishBundle
      public void bar() {}

      @FinishBundle
      public void baz() {}
    });
  }

  private static class PrivateDoFnClass extends DoFn<String, String> {
    final Invocations invocations = new Invocations(getClass().getName());

    @ProcessElement
    public void processThis(ProcessContext c) {
      invocations.wasProcessElementInvoked = true;
    }
  }

  @Test
  public void testLocalPrivateDoFnClass() throws Exception {
    PrivateDoFnClass fn = new PrivateDoFnClass();
    DoFnReflector reflector = underTest(fn);
    checkInvokeProcessElementWorks(reflector, fn.invocations);
  }

  @Test
  public void testStaticPackagePrivateDoFnClass() throws Exception {
    Invocations invocations = new Invocations("StaticPackagePrivateDoFn");
    DoFnReflector reflector =
        underTest(DoFnReflectorTestHelper.newStaticPackagePrivateDoFn(invocations));
    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testInnerPackagePrivateDoFnClass() throws Exception {
    Invocations invocations = new Invocations("InnerPackagePrivateDoFn");
    DoFnReflector reflector =
        underTest(new DoFnReflectorTestHelper().newInnerPackagePrivateDoFn(invocations));
    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testStaticPrivateDoFnClass() throws Exception {
    Invocations invocations = new Invocations("StaticPrivateDoFn");
    DoFnReflector reflector = underTest(DoFnReflectorTestHelper.newStaticPrivateDoFn(invocations));
    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testInnerPrivateDoFnClass() throws Exception {
    Invocations invocations = new Invocations("StaticInnerDoFn");
    DoFnReflector reflector =
        underTest(new DoFnReflectorTestHelper().newInnerPrivateDoFn(invocations));
    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testAnonymousInnerDoFnInOtherPackage() throws Exception {
    Invocations invocations = new Invocations("AnonymousInnerDoFnInOtherPackage");
    DoFnReflector reflector =
        underTest(new DoFnReflectorTestHelper().newInnerAnonymousDoFn(invocations));
    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testStaticAnonymousDoFnInOtherPackage() throws Exception {
    Invocations invocations = new Invocations("AnonymousStaticDoFnInOtherPackage");
    DoFnReflector reflector =
        underTest(DoFnReflectorTestHelper.newStaticAnonymousDoFn(invocations));
    checkInvokeProcessElementWorks(reflector, invocations);
  }

  @Test
  public void testPrivateProcessElement() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("process() must be public");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {
      @ProcessElement
      private void process() {}
    });
  }

  @Test
  public void testPrivateStartBundle() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("startBundle() must be public");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {
      @ProcessElement
      public void processElement() {}

      @StartBundle
      void startBundle() {}
    });
  }

  @Test
  public void testPrivateFinishBundle() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("finishBundle() must be public");
    thrown.expectMessage(getClass().getName() + "$");
    underTest(new DoFn<String, String>() {
      @ProcessElement
      public void processElement() {}

      @FinishBundle
      void finishBundle() {}
    });
  }

  @SuppressWarnings({"unused"})
  private void missingProcessContext() {}

  @Test
  public void testMissingProcessContext() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(getClass().getName()
        + "#missingProcessContext() must take a ProcessContext as its first argument");

    DoFnReflector.verifyProcessMethodArguments(
        getClass().getDeclaredMethod("missingProcessContext"));
  }

  @SuppressWarnings({"unused"})
  private void badProcessContext(String s) {}

  @Test
  public void testBadProcessContextType() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(getClass().getName()
        + "#badProcessContext(String) must take a ProcessContext as its first argument");

    DoFnReflector.verifyProcessMethodArguments(
        getClass().getDeclaredMethod("badProcessContext", String.class));
  }

  @SuppressWarnings({"unused"})
  private void badExtraContext(DoFn<Integer, String>.Context c, int n) {}

  @Test
  public void testBadExtraContext() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        "int is not a valid context parameter for method "
        + getClass().getName() + "#badExtraContext(Context, int). Should be one of [");

    DoFnReflector.verifyBundleMethodArguments(
        getClass().getDeclaredMethod("badExtraContext", Context.class, int.class));
  }

  @SuppressWarnings({"unused"})
  private void badExtraProcessContext(
      DoFn<Integer, String>.ProcessContext c, Integer n) {}

  @Test
  public void testBadExtraProcessContextType() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        "Integer is not a valid context parameter for method "
        + getClass().getName() + "#badExtraProcessContext(ProcessContext, Integer)"
        + ". Should be one of [BoundedWindow]");

    DoFnReflector.verifyProcessMethodArguments(
        getClass().getDeclaredMethod("badExtraProcessContext",
            ProcessContext.class, Integer.class));
  }

  @SuppressWarnings("unused")
  private int badReturnType() {
    return 0;
  }

  @Test
  public void testBadReturnType() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(getClass().getName() + "#badReturnType() must have a void return type");

    DoFnReflector.verifyProcessMethodArguments(getClass().getDeclaredMethod("badReturnType"));
  }

  @SuppressWarnings("unused")
  private void goodGenerics(
      DoFn<Integer, String>.ProcessContext c,
      DoFn.InputProvider<Integer> input,
      DoFn.OutputReceiver<String> output) {}

  @Test
  public void testValidGenerics() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "goodGenerics",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);
    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @SuppressWarnings("unused")
  private void goodWildcards(
      DoFn<Integer, String>.ProcessContext c,
      DoFn.InputProvider<?> input,
      DoFn.OutputReceiver<?> output) {}

  @Test
  public void testGoodWildcards() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "goodWildcards",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);
    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @SuppressWarnings("unused")
  private void goodBoundedWildcards(
      DoFn<Integer, String>.ProcessContext c,
      DoFn.InputProvider<? super Integer> input,
      DoFn.OutputReceiver<? super String> output) {}

  @Test
  public void testGoodBoundedWildcards() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "goodBoundedWildcards",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);
    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @SuppressWarnings("unused")
  private <InputT, OutputT> void goodTypeVariables(
      DoFn<InputT, OutputT>.ProcessContext c,
      DoFn.InputProvider<InputT> input,
      DoFn.OutputReceiver<OutputT> output) {}

  @Test
  public void testGoodTypeVariables() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "goodTypeVariables",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);
    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @SuppressWarnings("unused")
  private void badGenericTwoArgs(
      DoFn<Integer, String>.ProcessContext c,
      DoFn.InputProvider<Integer> input,
      DoFn.OutputReceiver<Integer> output) {}

  @Test
  public void testBadGenericsTwoArgs() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "badGenericTwoArgs",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Incompatible generics in context parameter "
        + "OutputReceiver<Integer> "
        + "for method " + getClass().getName()
        + "#badGenericTwoArgs(ProcessContext, InputProvider, OutputReceiver). Should be "
        + "OutputReceiver<String>");

    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @SuppressWarnings("unused")
  private void badGenericWildCards(
      DoFn<Integer, String>.ProcessContext c,
      DoFn.InputProvider<Integer> input,
      DoFn.OutputReceiver<? super Integer> output) {}

  @Test
  public void testBadGenericWildCards() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "badGenericWildCards",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Incompatible generics in context parameter "
        + "OutputReceiver<? super Integer> for method "
        + getClass().getName()
        + "#badGenericWildCards(ProcessContext, InputProvider, OutputReceiver). Should be "
        + "OutputReceiver<String>");

    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @SuppressWarnings("unused")
  private <InputT, OutputT> void badTypeVariables(DoFn<InputT, OutputT>.ProcessContext c,
      DoFn.InputProvider<InputT> input, DoFn.OutputReceiver<InputT> output) {}

  @Test
  public void testBadTypeVariables() throws Exception {
    Method method =
        getClass()
            .getDeclaredMethod(
                "badTypeVariables",
                DoFn.ProcessContext.class,
                DoFn.InputProvider.class,
                DoFn.OutputReceiver.class);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Incompatible generics in context parameter "
        + "OutputReceiver<InputT> for method " + getClass().getName()
        + "#badTypeVariables(ProcessContext, InputProvider, OutputReceiver). Should be "
        + "OutputReceiver<OutputT>");

    DoFnReflector.verifyProcessMethodArguments(method);
  }

  @Test
  public void testProcessElementException() throws Exception {
    DoFn<Integer, Integer> fn = new DoFn<Integer, Integer>() {
      @ProcessElement
      public void processElement(@SuppressWarnings("unused") ProcessContext c) {
        throw new IllegalArgumentException("bogus");
      }
    };

    thrown.expect(UserCodeException.class);
    thrown.expectMessage("bogus");
    DoFnReflector.of(fn.getClass()).bindInvoker(fn).invokeProcessElement(null, null);
  }

  @Test
  public void testStartBundleException() throws Exception {
    DoFn<Integer, Integer> fn = new DoFn<Integer, Integer>() {
      @StartBundle
      public void startBundle(@SuppressWarnings("unused") Context c) {
        throw new IllegalArgumentException("bogus");
      }

      @ProcessElement
      public void processElement(@SuppressWarnings("unused") ProcessContext c) {
      }
    };

    thrown.expect(UserCodeException.class);
    thrown.expectMessage("bogus");
    DoFnReflector.of(fn.getClass()).bindInvoker(fn).invokeStartBundle(null, null);
  }

  @Test
  public void testFinishBundleException() throws Exception {
    DoFn<Integer, Integer> fn = new DoFn<Integer, Integer>() {
      @FinishBundle
      public void finishBundle(@SuppressWarnings("unused") Context c) {
        throw new IllegalArgumentException("bogus");
      }

      @ProcessElement
      public void processElement(@SuppressWarnings("unused") ProcessContext c) {
      }
    };

    thrown.expect(UserCodeException.class);
    thrown.expectMessage("bogus");
    DoFnReflector.of(fn.getClass()).bindInvoker(fn).invokeFinishBundle(null, null);
  }
}
