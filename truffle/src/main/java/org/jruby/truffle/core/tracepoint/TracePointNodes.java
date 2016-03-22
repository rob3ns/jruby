/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.core.tracepoint;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;
import org.jruby.truffle.RubyContext;
import org.jruby.truffle.core.CoreClass;
import org.jruby.truffle.core.CoreMethod;
import org.jruby.truffle.core.CoreMethodArrayArgumentsNode;
import org.jruby.truffle.core.Layouts;
import org.jruby.truffle.core.UnaryCoreMethodNode;
import org.jruby.truffle.core.YieldingCoreMethodNode;
import org.jruby.truffle.core.kernel.TraceManager;
import org.jruby.truffle.language.NotProvided;
import org.jruby.truffle.language.objects.AllocateObjectNode;
import org.jruby.truffle.language.objects.AllocateObjectNodeGen;

@CoreClass(name = "TracePoint")
public abstract class TracePointNodes {

    @CoreMethod(names = "allocate", constructor = true)
    public abstract static class AllocateNode extends UnaryCoreMethodNode {

        @Child private AllocateObjectNode allocateNode;

        public AllocateNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            allocateNode = AllocateObjectNodeGen.create(context, sourceSection, null, null);
        }

        @Specialization
        public DynamicObject allocate(DynamicObject rubyClass) {
            return allocateNode.allocate(rubyClass, null, null, null, 0, null, null, null, false);
        }

    }

    @CoreMethod(names = "initialize", rest = true, needsBlock = true)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        public InitializeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject initialize(DynamicObject tracePoint, Object[] args, DynamicObject block) {
            Layouts.TRACE_POINT.setTags(tracePoint, new String[]{TraceManager.LINE_TAG});
            Layouts.TRACE_POINT.setProc(tracePoint, block);
            return tracePoint;
        }

    }

    @CoreMethod(names = "enable", needsBlock = true)
    public abstract static class EnableNode extends YieldingCoreMethodNode {

        public EnableNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean enable(VirtualFrame frame, DynamicObject tracePoint, NotProvided block) {
            return enable(frame, tracePoint, (DynamicObject) null);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean enable(VirtualFrame frame, final DynamicObject tracePoint, DynamicObject block) {
            CompilerDirectives.bailout("TracePoint#enable can't be compiled");

            EventBinding<?> eventBinding = Layouts.TRACE_POINT.getEventBinding(tracePoint);
            final boolean alreadyEnabled = eventBinding != null;

            if (!alreadyEnabled) {
                eventBinding = createEventBinding(getContext(), tracePoint);
                Layouts.TRACE_POINT.setEventBinding(tracePoint, eventBinding);
            }

            if (block != null) {
                try {
                    yield(frame, block);
                } finally {
                    if (!alreadyEnabled) {
                        eventBinding.dispose();
                        Layouts.TRACE_POINT.setEventBinding(tracePoint, null);
                    }
                }
            }

            return alreadyEnabled;
        }

        public static EventBinding<?> createEventBinding(final RubyContext context, final DynamicObject tracePoint) {
            return context.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(Layouts.TRACE_POINT.getTags(tracePoint)).build(), new ExecutionEventNodeFactory() {
                @Override
                public ExecutionEventNode create(EventContext eventContext) {
                    return new TracePointEventNode(context, tracePoint);
                }
            });
        }

    }

    @CoreMethod(names = "disable", needsBlock = true)
    public abstract static class DisableNode extends YieldingCoreMethodNode {

        public DisableNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean disable(VirtualFrame frame, DynamicObject tracePoint, NotProvided block) {
            return disable(frame, tracePoint, (DynamicObject) null);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean disable(VirtualFrame frame, final DynamicObject tracePoint, DynamicObject block) {
            CompilerDirectives.bailout("TracePoint#disable can't be compiled");

            EventBinding<?> eventBinding = Layouts.TRACE_POINT.getEventBinding(tracePoint);
            final boolean alreadyEnabled = eventBinding != null;

            if (alreadyEnabled) {
                eventBinding.dispose();
                Layouts.TRACE_POINT.setEventBinding(tracePoint, null);
            }

            if (block != null) {
                try {
                    yield(frame, block);
                } finally {
                    if (alreadyEnabled) {
                        eventBinding = EnableNode.createEventBinding(getContext(), tracePoint);
                        Layouts.TRACE_POINT.setEventBinding(tracePoint, eventBinding);
                    }
                }
            }

            return alreadyEnabled;
        }

    }

    @CoreMethod(names = "enabled?")
    public abstract static class EnabledNode extends CoreMethodArrayArgumentsNode {

        public EnabledNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean enabled(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getEventBinding(tracePoint) != null;
        }

    }

    @CoreMethod(names = "event")
    public abstract static class EventNode extends CoreMethodArrayArgumentsNode {

        public EventNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject event(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getEvent(tracePoint);
        }

    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends CoreMethodArrayArgumentsNode {

        public PathNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject path(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getPath(tracePoint);
        }

    }

    @CoreMethod(names = "lineno")
    public abstract static class LineNode extends CoreMethodArrayArgumentsNode {

        public LineNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public int line(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getLine(tracePoint);
        }

    }

    @CoreMethod(names = "binding")
    public abstract static class BindingNode extends CoreMethodArrayArgumentsNode {

        public BindingNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject binding(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getBinding(tracePoint);
        }

    }

}