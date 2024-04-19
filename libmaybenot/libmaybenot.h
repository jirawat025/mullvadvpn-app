/* Generated with cbindgen:0.26.0 */

/* Warning, this file is autogenerated by cbindgen. Don't modify this manually. */

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

enum MaybenotError {
  MaybenotError_Ok = 0,
  MaybenotError_MachineStringNotUtf8 = 1,
  MaybenotError_InvalidMachineString = 2,
  MaybenotError_StartFramework = 3,
  MaybenotError_UnknownMachine = 4,
};
typedef uint32_t MaybenotError;

enum MaybenotEventType {
  /**
   * We sent a normal packet.
   */
  MaybenotEventType_NonpaddingSent = 0,
  /**
   * We received a normal packet.
   */
  MaybenotEventType_NonpaddingReceived = 1,
  /**
   * We send a padding packet.
   */
  MaybenotEventType_PaddingSent = 2,
  /**
   * We received a padding packet.
   */
  MaybenotEventType_PaddingReceived = 3,
};
typedef uint32_t MaybenotEventType;

/**
 * A running Maybenot instance.
 *
 * - Create it [ffi::maybenot_start].
 * - Feed it actions using [ffi::maybenot_on_event].
 * - Stop it using [ffi::maybenot_stop].
 */
typedef struct Maybenot Maybenot;

typedef struct MaybenotEvent {
  MaybenotEventType event_type;
  /**
   * The number of bytes that was sent or received.
   */
  uint16_t xmit_bytes;
  /**
   * The ID of the machine that triggered the event, if any.
   */
  uint64_t machine;
} MaybenotEvent;

typedef struct MaybenotDuration {
  /**
   * Number of whole seconds
   */
  uint64_t secs;
  /**
   * A nanosecond fraction of a second.
   */
  uint32_t nanos;
} MaybenotDuration;

enum MaybenotAction_Tag {
  MaybenotAction_Cancel = 0,
  /**
   * Send a padding packet.
   */
  MaybenotAction_InjectPadding = 1,
  MaybenotAction_BlockOutgoing = 2,
};
typedef uint32_t MaybenotAction_Tag;

typedef struct MaybenotAction_Cancel_Body {
  /**
   * The machine that generated the action.
   */
  uint64_t machine;
} MaybenotAction_Cancel_Body;

typedef struct MaybenotAction_InjectPadding_Body {
  /**
   * The machine that generated the action.
   */
  uint64_t machine;
  /**
   * The time to wait before injecting a padding packet.
   */
  struct MaybenotDuration timeout;
  bool replace;
  bool bypass;
  /**
   * The size of the padding packet.
   */
  uint16_t size;
} MaybenotAction_InjectPadding_Body;

typedef struct MaybenotAction_BlockOutgoing_Body {
  /**
   * The machine that generated the action.
   */
  uint64_t machine;
  /**
   * The time to wait before blocking.
   */
  struct MaybenotDuration timeout;
  bool replace;
  bool bypass;
  /**
   * How long to block.
   */
  struct MaybenotDuration duration;
} MaybenotAction_BlockOutgoing_Body;

typedef struct MaybenotAction {
  MaybenotAction_Tag tag;
  union {
    MaybenotAction_Cancel_Body cancel;
    MaybenotAction_InjectPadding_Body inject_padding;
    MaybenotAction_BlockOutgoing_Body block_outgoing;
  };
} MaybenotAction;

/**
 * Start a new [Maybenot] instance.
 *
 * `machines_str` must be a null-terminated UTF-8 string, containing LF-separated machines.
 */
MaybenotError maybenot_start(const int8_t *machines_str,
                             double max_padding_bytes,
                             double max_blocking_bytes,
                             uint16_t mtu,
                             struct Maybenot **out);

/**
 * Stop a running [Maybenot] instance.
 *
 * This will free the maybenot pointer.
 */
void maybenot_stop(struct Maybenot *this_);

/**
 * Feed an event to the [Maybenot] instance.
 *
 * This may generate [super::MaybenotAction]s that will be sent to the callback provided to
 * [maybenot_start]. `user_data` will be passed to the callback as-is, it will not be read or
 * modified.
 */
MaybenotError maybenot_on_event(struct Maybenot *this_,
                                struct MaybenotEvent event,
                                struct MaybenotAction *actions,
                                uint64_t *num_actions_out);
