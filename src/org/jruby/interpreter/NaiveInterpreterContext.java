/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.interpreter;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.RubyException;

import org.jruby.compiler.ir.IRMethod;
import org.jruby.compiler.ir.operands.Label;

/**
 *
 * @author enebo
 */
public class NaiveInterpreterContext implements InterpreterContext {
    private final Ruby runtime;
    private final ThreadContext context;
    protected Object returnValue;
    protected Object self;
    protected IRubyObject[] parameters;
    protected Object[] temporaryVariables;
    protected Object[] localVariables;
    protected Frame frame;
    protected Block block;
    protected Block.Type blockType;
    protected DynamicScope currDynScope = null;
    protected boolean allocatedDynScope = false;
    protected RubyException currException = null;

    private Label methodExitLabel = null;

	 // currentModule is:
	 // - self if we are executing a class method of 'self'
	 // - self.getMetaClass() if we are executing an instance method of 'self'
	 // - the class in which the closure is lexically defined in if we are executing a closure
    public NaiveInterpreterContext(ThreadContext context, RubyModule currentModule, IRubyObject self, String name, int localVariablesSize, int temporaryVariablesSize, IRubyObject[] parameters, Block block, Block.Type blockType) {
        context.preMethodFrameOnly(currentModule, name, self, block);
        this.frame = context.getCurrentFrame();

        this.context = context;
        this.runtime = context.getRuntime();
        this.self = self;
        this.parameters = parameters;
        this.localVariables = localVariablesSize > 0 ? new Object[localVariablesSize] : null;
        this.temporaryVariables = temporaryVariablesSize > 0 ? new Object[temporaryVariablesSize] : null;
        this.block = block;
		  // SSS FIXME: Can it happen that (block.type != blockType)?
		  this.blockType = blockType;
    }

    public Ruby getRuntime() {
        return runtime;
    }

    public Block getBlock() {
        return block;
    }

    public boolean inLambda() {
        return (blockType != null) && (blockType == Block.Type.LAMBDA);
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public void setDynamicScope(DynamicScope s) {
        this.currDynScope = s;
    }

    public void allocateSharedBindingScope(IRMethod method) {
        this.allocatedDynScope = true;
        this.currDynScope = new org.jruby.runtime.scope.SharedBindingDynamicScope(method.getStaticScope(), method);
        context.pushScope(this.currDynScope);
    }

    public DynamicScope getSharedBindingScope() {
        return this.currDynScope;
    }

    // SSS: Should get rid of this and add a FreeBinding instruction
    public boolean hasAllocatedDynamicScope() {
        return this.allocatedDynScope;
    }

    public Object getReturnValue() {
        // FIXME: Maybe returnValue is a sure thing and we don't need this check.  Should be this way.
        return returnValue == null ? runtime.getNil() : returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public Object getTemporaryVariable(int offset) {
        return temporaryVariables[offset];
    }

    public Object setTemporaryVariable(int offset, Object value) {
        Object oldValue = temporaryVariables[offset];

        temporaryVariables[offset] = value;

        return oldValue;
    }

    // Post-inlining
    public void updateLocalVariablesCount(int n) {
        // SSS FIXME: use System.arraycopy
        Object[] oldLocalVars = this.localVariables;
        this.localVariables = new Object[n];
        for (int i = 0; i < oldLocalVars.length; i++) this.localVariables[i] = oldLocalVars[i];
    }

    public Object getSharedBindingVariable(int bindingSlot) {
        Object value = currDynScope.getValue(bindingSlot, 0);
        if (value == null) value = getRuntime().getNil();
        return value;
    }

    public void setSharedBindingVariable(int bindingSlot, Object value) {
        currDynScope.setValueDepthZero((IRubyObject)value, bindingSlot);
    }

    public Object getLocalVariable(int offset) {
        Object o = localVariables[offset];
		  return (o == null) ? getRuntime().getNil() : o;
    }

    public Object setLocalVariable(int offset, Object value) {
        Object oldValue = localVariables[offset];

        localVariables[offset] = value;

        return oldValue;
    }

    public ThreadContext getContext() {
        return context;
    }

    public IRubyObject[] setNewParameters(IRubyObject[] newParams) {
        IRubyObject[] oldParams = parameters;
        this.parameters = newParams;
        return oldParams;
    }

    public Object getParameter(int offset) {
        return parameters[offset];
    }

    public int getParameterCount() {
        return parameters.length;
    }

    public Object getSelf() {
        return self;
    }

    public Frame getFrame() {
        return frame;
    }

    public void setFrame(Frame frame) {
        this.frame = frame;
    }
    // FIXME: We have this as a var somewhere else
    private IRubyObject[] NO_PARAMS = new IRubyObject[0];

    public IRubyObject[] getParametersFrom(int argIndex) {
        int length = parameters.length - argIndex;
        
        if (length <= 0) return NO_PARAMS;

        IRubyObject[] args = new IRubyObject[length];
        System.arraycopy(parameters, argIndex, args, 0, length);

        return args;
    }

    public void setMethodExitLabel(Label l) {
        methodExitLabel = l;
    }

    public Label getMethodExitLabel() {
        return methodExitLabel;
    }

    // Set the most recently raised exception
    public void setException(RubyException e) {
        // SSS FIXME: More things to be done besides this
        currException = e;
    }

    // SSS FIXME: Should we get-and-clear instead of just get?
    // Get the most recently raised exception
    public RubyException getException() {
        return currException;
    }
}
