package org.jruby.ir.instructions;

import org.jruby.RubySymbol;
import org.jruby.ir.*;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.operands.Variable;
import org.jruby.ir.persistence.IRReaderDecoder;
import org.jruby.ir.persistence.IRWriterEncoder;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.ir.transformations.inlining.InlineCloneInfo;
import org.jruby.ir.transformations.inlining.SimpleCloneInfo;

public class NonlocalReturnInstr extends ReturnBase implements FixedArityInstr {
    public final RubySymbol methodName; // Primarily a debugging aid

    public NonlocalReturnInstr(Operand returnValue, RubySymbol methodName) {
        super(Operation.NONLOCAL_RETURN, returnValue);
        this.methodName = methodName;
    }

    @Override
    public String[] toStringNonOperandArgs() {
        return new String[] { "name: " + methodName };
    }

    public boolean computeScopeFlags(IRScope scope) {
        scope.getFlags().add(IRFlags.HAS_NONLOCAL_RETURNS);
        return true;
    }

    @Override
    public Instr clone(CloneInfo info) {
        if (info instanceof SimpleCloneInfo) return new NonlocalReturnInstr(getReturnValue().cloneForInlining(info), methodName);

        InlineCloneInfo ii = (InlineCloneInfo) info;
        if (ii.isClosure()) {
            if (ii.getHostScope() instanceof IRMethod) {
                // Lexically contained non-local returns can return directly if the live in the method they are inlining to.
                if (((InlineCloneInfo) info).getScopeBeingInlined().isScopeContainedBy(ii.getHostScope())) {
                    return new ReturnInstr(getReturnValue().cloneForInlining(ii));
                }

                // Treat like inlining of a regular method-return (note: a jump is added to exit so this copy
                // actually ends up being the methods return value).
                Variable v = ii.getCallResultVariable();
                return v == null ? null : new CopyInstr(v, getReturnValue().cloneForInlining(ii));
            }

            return new NonlocalReturnInstr(getReturnValue().cloneForInlining(ii), methodName);
        } else {
            throw new UnsupportedOperationException("Nonlocal returns shouldn't show up outside closures.");
        }
    }

    @Override
    public void encode(IRWriterEncoder e) {
        super.encode(e);
        e.encode(getReturnValue());
        e.encode(methodName);
    }

    public static NonlocalReturnInstr decode(IRReaderDecoder d) {
        return new NonlocalReturnInstr(d.decodeOperand(), d.decodeSymbol());
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.NonlocalReturnInstr(this);
    }
}
