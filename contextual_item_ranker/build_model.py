import tensorflow as tf
from typing import Dict
import math

import logging as log
import sys

log.basicConfig(
    level=log.DEBUG,
    stream=sys.stdout,
)

log.info(f"tensorflow version: {tf.__version__}")

if __name__ == '__main__':
    valid_context_keys = [
        "key1",
        "key2",
        "key3",
        "key4",
    ]

    item_id = tf.keras.Input(shape=(None,), dtype=tf.string, name="item_id")
    context_values = [
        tf.keras.Input(shape=(None,), dtype=tf.string, name=k)
        for k in valid_context_keys
    ]

    flattened_context_keys = tf.concat(context_values, axis=0)
    context_lengths = tf.strings.length(flattened_context_keys)
    total_context_characters = tf.reduce_sum(context_lengths)

    reshaped_item_id = tf.reshape(item_id, (-1, 1))
    item_id_lengths = tf.strings.length(reshaped_item_id)


    total_lengths = item_id_lengths + total_context_characters

    multiplied_ids = math.pi * tf.cast(total_lengths, tf.float32)
    scores = tf.reshape(multiplied_ids, (-1,))

    model = tf.keras.Model(
        inputs={
            **{"item_id": item_id},
            **dict(zip(valid_context_keys, context_values))
        },
        outputs=scores
    )

    known_keys = ["item_id"] + valid_context_keys

    input_signature = {
        k: tf.TensorSpec(shape=(None,), dtype=tf.string, name=k)
        for k in known_keys
    }

    @tf.function(input_signature=[input_signature])
    def serving_fn(inputs: Dict[str, tf.Tensor]):
        return {"scores": model([inputs[k] for k in known_keys])}

    model.save(
        "model_dir",
        signatures={
            tf.saved_model.DEFAULT_SERVING_SIGNATURE_DEF_KEY: serving_fn,
            tf.saved_model.PREDICT_METHOD_NAME: serving_fn,
        }
    )
