package com.thiru.wealthlens.taxplanning.isolation;

/** What a child process gave back: its reply, or why there is none (W-133a, from W-131f's reader). */
public sealed interface ChildReply {

    /** The bytes the child wrote, at most the isolation's cap; still to be parsed strictly by the caller. */
    record Answered(byte[] bytes) implements ChildReply {

        public Answered {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    /** Too slow, a non-zero exit, a reply too large, or one that could not be read: the reason in words. */
    record Failed(String reason) implements ChildReply {
    }
}
