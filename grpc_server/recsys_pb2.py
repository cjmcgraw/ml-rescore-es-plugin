# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: recsys.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor.FileDescriptor(
  name='recsys.proto',
  package='recsys',
  syntax='proto3',
  serialized_options=_b('\n\005qarlmP\001'),
  serialized_pb=_b('\n\x0crecsys.proto\x12\x06recsys\"=\n\x12RecommenderRequest\x12\x16\n\nexampleids\x18\x01 \x03(\x03\x42\x02\x10\x01\x12\x0f\n\x07\x63ontext\x18\x02 \x01(\t\"*\n\x13RecommenderResponse\x12\x13\n\x07outputs\x18\x01 \x03(\x02\x42\x02\x10\x01\x32U\n\x0bRecommender\x12\x46\n\trecommend\x12\x1a.recsys.RecommenderRequest\x1a\x1b.recsys.RecommenderResponse\"\x00\x42\t\n\x05qarlmP\x01\x62\x06proto3')
)




_RECOMMENDERREQUEST = _descriptor.Descriptor(
  name='RecommenderRequest',
  full_name='recsys.RecommenderRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='exampleids', full_name='recsys.RecommenderRequest.exampleids', index=0,
      number=1, type=3, cpp_type=2, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=_b('\020\001'), file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='context', full_name='recsys.RecommenderRequest.context', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=24,
  serialized_end=85,
)


_RECOMMENDERRESPONSE = _descriptor.Descriptor(
  name='RecommenderResponse',
  full_name='recsys.RecommenderResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='outputs', full_name='recsys.RecommenderResponse.outputs', index=0,
      number=1, type=2, cpp_type=6, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=_b('\020\001'), file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=87,
  serialized_end=129,
)

DESCRIPTOR.message_types_by_name['RecommenderRequest'] = _RECOMMENDERREQUEST
DESCRIPTOR.message_types_by_name['RecommenderResponse'] = _RECOMMENDERRESPONSE
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

RecommenderRequest = _reflection.GeneratedProtocolMessageType('RecommenderRequest', (_message.Message,), {
  'DESCRIPTOR' : _RECOMMENDERREQUEST,
  '__module__' : 'recsys_pb2'
  # @@protoc_insertion_point(class_scope:recsys.RecommenderRequest)
  })
_sym_db.RegisterMessage(RecommenderRequest)

RecommenderResponse = _reflection.GeneratedProtocolMessageType('RecommenderResponse', (_message.Message,), {
  'DESCRIPTOR' : _RECOMMENDERRESPONSE,
  '__module__' : 'recsys_pb2'
  # @@protoc_insertion_point(class_scope:recsys.RecommenderResponse)
  })
_sym_db.RegisterMessage(RecommenderResponse)


DESCRIPTOR._options = None
_RECOMMENDERREQUEST.fields_by_name['exampleids']._options = None
_RECOMMENDERRESPONSE.fields_by_name['outputs']._options = None

_RECOMMENDER = _descriptor.ServiceDescriptor(
  name='Recommender',
  full_name='recsys.Recommender',
  file=DESCRIPTOR,
  index=0,
  serialized_options=None,
  serialized_start=131,
  serialized_end=216,
  methods=[
  _descriptor.MethodDescriptor(
    name='recommend',
    full_name='recsys.Recommender.recommend',
    index=0,
    containing_service=None,
    input_type=_RECOMMENDERREQUEST,
    output_type=_RECOMMENDERRESPONSE,
    serialized_options=None,
  ),
])
_sym_db.RegisterServiceDescriptor(_RECOMMENDER)

DESCRIPTOR.services_by_name['Recommender'] = _RECOMMENDER

# @@protoc_insertion_point(module_scope)
