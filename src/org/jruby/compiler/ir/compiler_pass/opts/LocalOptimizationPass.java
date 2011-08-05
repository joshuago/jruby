package org.jruby.compiler.ir.compiler_pass.opts;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.jruby.compiler.ir.IRClosure;
import org.jruby.compiler.ir.IRExecutionScope;
import org.jruby.compiler.ir.IRMethod;
import org.jruby.compiler.ir.IRModule;
import org.jruby.compiler.ir.IRScope;
import org.jruby.compiler.ir.instructions.CallInstr;
import org.jruby.compiler.ir.instructions.CopyInstr;
import org.jruby.compiler.ir.instructions.Instr;
import org.jruby.compiler.ir.instructions.JumpInstr;
import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.CodeVersion;
import org.jruby.compiler.ir.operands.Array;
import org.jruby.compiler.ir.operands.Fixnum;
import org.jruby.compiler.ir.operands.Float;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Constant;
import org.jruby.compiler.ir.operands.Variable;
import org.jruby.compiler.ir.compiler_pass.CompilerPass;

public class LocalOptimizationPass implements CompilerPass
{
    public LocalOptimizationPass() { }

    // Should we run this pass on the current scope before running it on nested scopes?
    public boolean isPreOrder() { return false; }

    public void run(IRScope s) {
        if (s instanceof IRExecutionScope) {
            IRExecutionScope es = (IRExecutionScope)s;

            // Run this pass on nested closures first!
            // This let us compute execute scope flags for a method based on what all nested closures do
            List<IRClosure> closures = es.getClosures();
            for (IRClosure c: closures)
                run(c);

            // Now, run on current scope
            runLocalOpts(es);

            // Only after running local opts, compute various execution scope flags
            es.computeExecutionScopeFlags();
        }
    }

    private static void recordSimplification(Variable res, Operand val, Map<Operand, Operand> valueMap, Map<Variable, List<Variable>> simplificationMap) {
        valueMap.put(res, val);

        // If 'res' has simplified to a variable, then record this reverse mapping
        // so, we can respect Read-After-Write scenarios for 'val' and purge this
        // simplification when 'val' gets modified
        if (val instanceof Variable) {
           Variable v = (Variable)val;
           List<Variable> x = simplificationMap.get(val);
           if (x == null) {
              x = new java.util.ArrayList<Variable>();
              simplificationMap.put(v, x);
           }
           x.add(res);
        }
    }

    private static void runLocalOpts(IRExecutionScope s) {
        // Reset value map if this instruction is the start/end of a basic block
        //
        // Right now, calls are considered hard boundaries for optimization and
        // information cannot be propagated across them!
        //
        // SSS FIXME: Rather than treat all calls with a broad brush, what we need
        // is to capture different attributes about a call :
        //   - uses closures
        //   - known call target
        //   - can modify scope,
        //   - etc.
        //
        // This information is probably already present in the AST Inspector
        Label deoptLabel = s.getNewLabel();
        Map<Operand,Operand> valueMap = new HashMap<Operand,Operand>();
        Map<Variable,List<Variable>> simplificationMap = new HashMap<Variable,List<Variable>>();
        Map<String,CodeVersion> versionMap = new HashMap<String,CodeVersion>();
        ListIterator<Instr> instrs = s.getInstrs().listIterator();
        while (instrs.hasNext()) {
            Instr i = instrs.next();
            Operation iop = i.operation;
            if (iop.startsBasicBlock()) {
                valueMap = new HashMap<Operand,Operand>();
                simplificationMap = new HashMap<Variable,List<Variable>>();
                versionMap = new HashMap<String, CodeVersion>();
            }

            // Simplify instruction and record mapping between target variable and simplified value
//            System.out.println("BEFORE: " + i);
            Operand  val = i.simplifyAndGetResult(valueMap);
            Variable res = i.getResult();
//            System.out.println("For " + i + "; dst = " + res + "; val = " + val);
//            System.out.println("AFTER: " + i);
            if (val != null && res != null && res != val) {
                recordSimplification(res, val, valueMap, simplificationMap);
            }
            // Optimize some core class method calls for constant values
            else if (iop.isCall()) {
                val = null;
                CallInstr call = (CallInstr) i;
                Operand   r    = call.getReceiver(); 
                // SSS FIXME: r can be null for ruby/jruby internal call instructions!
                // Cannot optimize them as of now.
                if (r != null) {
                    // If 'r' is not a constant, it could actually be a compound value!
                    // Look in our value map to see if we have a simplified value for the receiver.
                    if (!r.isConstant()) {
                        Operand v = valueMap.get(r);
                        if (v != null)
                            r = v;
                    }
                }
            }

            // Purge all entries in valueMap that have 'res' as their simplified value to take care of RAW scenarios (because we aren't in SSA form yet!)
            if (res != null) {
                List<Variable> simplifiedVars = simplificationMap.get(res);
                if (simplifiedVars != null) {
                    for (Variable v: simplifiedVars)
                        valueMap.remove(v);
                    simplificationMap.remove(res);
                }
            }

            // If the call has been optimized away in the previous step, it is no longer a hard boundary for opts!
            if (iop.endsBasicBlock() || (iop.isCall() && !i.isDead())) {
                valueMap = new HashMap<Operand,Operand>();
                simplificationMap = new HashMap<Variable,List<Variable>>();
                versionMap = new HashMap<String, CodeVersion>();
            }
        }
    }
}
