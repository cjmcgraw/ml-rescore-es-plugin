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
    item_id = tf.keras.Input(shape=(None,), dtype=tf.float32, name="item_id")
    reshaped_item_id = tf.reshape(item_id, (-1, 1))
    item_id_lengths = tf.math.floordiv(tf.math.log(reshaped_item_id), tf.math.log(10.0)) + 1
    multiplied_ids = math.e * tf.cast(item_id_lengths, tf.float32)
    scores = tf.reshape(multiplied_ids, (-1,))

    model = tf.keras.Model(
        inputs=[item_id],
        outputs=scores
    )

    results = model.predict([1, 10, 100, 1_000, 10_000, 100_000])
    print(results)

    input_signature = {
        "item_id": tf.TensorSpec(shape=(None,), dtype=tf.uint32, name="item_id")
    }

    @tf.function(input_signature=[input_signature])
    def serving_fn(inputs: Dict[str, tf.Tensor]):
        data = tf.cast(inputs['item_id'], tf.float32)
        return {"scores": model(data)}

    model.save(
        "model_dir",
        signatures={
            tf.saved_model.DEFAULT_SERVING_SIGNATURE_DEF_KEY: serving_fn,
            tf.saved_model.PREDICT_METHOD_NAME: serving_fn,
        }
    )
