#include "log_producer.h"

LRESULT LogProducer::LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam)
{
	return LRESULT();
}

LogProducer* LogProducer::GetInstanceFromHook()
{
	return nullptr;
}

void LogProducer::UpdateCapsLockState(WORD vkCode, WPARAM wParam)
{
}

void LogProducer::produce(WPARAM wParam, const KBDLLHOOKSTRUCT& kbStruct)
{
}

void LogProducer::init(std::mutex& m, std::counting_semaphore<MAX_COUNT>& sem)
{
}
