package org.jruby.ir.instructions;

import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.operands.Variable;
import org.jruby.ir.transformations.inlining.InlinerInfo;

public class ReceiveExceptionInstr extends Instr implements ResultInstr {
    private Variable result;
    
    public ReceiveExceptionInstr(Variable result) {
        super(Operation.RECV_EXCEPTION);
        
        assert result != null : "ResultExceptionInstr result is null";
        
        this.result = result;
    }

    public Operand[] getOperands() {
        return EMPTY_OPERANDS;
    }

    public Variable getResult() {
        return result;
    }

    public void updateResult(Variable v) {
        this.result = v;
    }

    @Override
    public Instr cloneForInlining(InlinerInfo ii) {
        return new ReceiveExceptionInstr(ii.getRenamedVariable(result));
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.ReceiveExceptionInstr(this);
    }
}
