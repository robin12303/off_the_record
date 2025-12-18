#include <Windows.h>
#include <string> 
#include <unordered_map>
#include <iostream>
#include <sstream>
#include <iomanip>
std::string VkCodeToString(
	WORD vkCode, bool shiftPressed, bool capsLockOn);
bool IsModifierKey(WORD vkCode);
bool IsSpecialKey(WORD vkCode);
std::string GetBaseKeyString(WORD vkCode);
std::string GetShiftedKeyString(WORD vkCode);
std::string GetNumpadKeyString(WORD vkCode);

std::string GetCurrentTimestamp();
std::string FormatTime(const SYSTEMTIME& st);
std::string VkCodeToHexString(WORD vkCode);
bool IsKeyPressed(WORD vkCode);