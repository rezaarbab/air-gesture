package ir.airgesture.model

/** Which sensor is read. Only one at a time, so two decoders can never fire on the same motion. */
enum class SenseMode { HAND, PROX }
