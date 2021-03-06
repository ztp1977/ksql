/**
 * Copyright 2017 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.confluent.ksql.serde.avro;

import io.confluent.ksql.physical.GenericRow;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class KsqlGenericRowAvroSerializer implements Serializer<GenericRow> {

  public static final String AVRO_SERDE_SCHEMA_CONFIG = "avro.serde.schema";
  public static final String AVRO_SERDE_SCHEMA_DIRECTORY_DEFAULT = "/tmp/";

  private final org.apache.kafka.connect.data.Schema schema;

  String rowSchema;
  Schema.Parser parser;
  Schema avroSchema;
  GenericDatumWriter<GenericRecord> writer;
  ByteArrayOutputStream output;
  Encoder encoder;
  List<Schema.Field> fields;

  public KsqlGenericRowAvroSerializer(org.apache.kafka.connect.data.Schema schema) {
    this.schema = schema;
  }

  @Override
  public void configure(final Map<String, ?> map, final boolean b) {
    rowSchema = (String) map.get(AVRO_SERDE_SCHEMA_CONFIG);
    if (rowSchema == null) {
      throw new SerializationException("Avro schema is not set for the serializer.");
    }
    parser = new Schema.Parser();
    avroSchema = parser.parse(rowSchema);
    fields = avroSchema.getFields();
    writer = new GenericDatumWriter<>(avroSchema);
  }

  @Override
  public byte[] serialize(final String topic, final GenericRow genericRow) {
    if (genericRow == null) {
      return null;
    }
    GenericRecord avroRecord = new GenericData.Record(avroSchema);
    for (int i = 0; i < genericRow.getColumns().size(); i++) {
      if (fields.get(i).schema().getType() == Schema.Type.ARRAY) {
        avroRecord.put(fields.get(i).name(), Arrays.asList((Object[]) genericRow.getColumns().get(i)));
      } else {
        avroRecord.put(fields.get(i).name(), genericRow.getColumns().get(i));
      }

    }

    try {
      output = new ByteArrayOutputStream();
      encoder = EncoderFactory.get().binaryEncoder(output, null);
      writer.write(avroRecord, encoder);
      encoder.flush();
      output.flush();
    } catch (IOException e) {
      throw new SerializationException("Error serializing AVRO message", e);
    }
    return output.toByteArray();
  }

  @Override
  public void close() {

  }
}
