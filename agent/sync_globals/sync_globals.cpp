#include "sync_globals.h"


std::counting_semaphore<INT_MAX> sem{ 0 };
std::queue<json::object> q;
std::mutex m;
std::atomic<bool> run_key_event{false};

 

// ========================================
 