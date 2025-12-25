target_include_directories(agent_v1 PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/common
  ${CMAKE_CURRENT_SOURCE_DIR}/logger
  ${CMAKE_CURRENT_SOURCE_DIR}/connection
  ${CMAKE_CURRENT_SOURCE_DIR}/router
  ${CMAKE_CURRENT_SOURCE_DIR}/heartbeat
  ${CMAKE_CURRENT_SOURCE_DIR}/metric
)
