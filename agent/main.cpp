#include "sync_globals.h" 
#include "ws_heartbeat.h" 
#include "producer.h"  
#include "ws_consumer.h"
namespace json = boost::json;
const std::string host = "[2406:5900:107c:248c:1298:9f9e:6e5d:653f]";
const std::string port = "8080";
const std::string target = "/ws/agent";
void RunIoContext(boost::asio::io_context& ioc);
int main()
{
    // sys_info
    auto sys_info = getSpec();

    json::object sys_info_json;
    sys_info_json["cpuName"] = sys_info.cpu_name;
    sys_info_json["gpuName"] = sys_info.gpu_name;
    sys_info_json["ramTotalMb"] = sys_info.ram;
    sys_info_json["osName"] = sys_info.os_name;
    sys_info_json["osVersion"] = sys_info.os_version;
    sys_info_json["machineGuid"] = sys_info.machine_guid;
    sys_info_json["hostName"] = sys_info.host_name;
    auto payload = json::serialize(sys_info_json);
    std::cout << "osName: " << sys_info.os_name << "\n";
    boost::asio::io_context ioc;
    auto conn = std::make_shared<WsConnection>(ioc);

    conn->start(host, port, target);

    Producer producer; 
     
    // I/O 컨텍스트를 별도 스레드에서 실행
    std::thread io_thread([&ioc]() {
        try {
            ioc.run();
            std::cout << "I/O context finished." << std::endl;
        }
        catch (const std::exception& e) {
            std::cerr << "I/O context error: " << e.what() << std::endl;
        }
        });

    auto ws_heartbeat = std::make_shared<WsHeartbeat>(ioc, conn, payload, 5s); 
    auto ws_consumer = std::make_shared<WsConsumer>(conn);
    ws_heartbeat->start();  
    ws_consumer->run();

    producer.start(); 
   
    // Windows 메시지 루프
    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    // 정리
    producer.Stop();
    //consumer.stop();
    ws_consumer->stop();
    // I/O 컨텍스트 정리
    ioc.stop();
    if (io_thread.joinable()) {
        io_thread.join();
    }
    std::cout << "Application exited." << std::endl;
    return 0;
}

void RunIoContext(boost::asio::io_context& ioc) {
    ioc.run();
}
 