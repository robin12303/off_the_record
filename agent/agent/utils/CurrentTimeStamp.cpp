#include "CurrentTimeStamp.h"
 
std::string getCurrentTimestamp()
{
    std::time_t t = std::time(nullptr);

    std::tm tm{};
    localtime_s(&tm, &t); //  MSVC ±«¿Â

    char buf[32];
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &tm);

    std::ostringstream oss;
    oss << "[" << buf << "]";
    return oss.str();
}
