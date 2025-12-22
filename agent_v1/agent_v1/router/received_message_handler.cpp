#include "received_message_handler.h"
MessageHandler::MessageHandler(std::shared_ptr<Connection> conn)
    : conn_(std::move(conn))
{

}
MessageHandler::~MessageHandler()
{
}

void MessageHandler::start()
{
	if (t_.joinable()) return;
	t_ = std::jthread([this](std::stop_token st) { loop(st); });
}

void MessageHandler::stop()
{
    if (t_.joinable()) {
        t_.request_stop();
        t_ = std::jthread();
    }
}

void MessageHandler::loop(std::stop_token st)
{
    while (!st.stop_requested()) {
        std::cout << "wating recv... " << "\n";
        recv_sem.acquire();
        std::string msg = "null";
        {
            std::lock_guard<std::mutex> lk(recv_m);
            msg = recv_q.front();
            recv_q.pop();
        }
        std::cout << "[MessageHandler] received: " << msg << "\n";  
        boost::system::error_code ec;
        json::object data = json::parse(msg).as_object();
        if (ec) {
            std::cout << ec.message() << "\n";
        }
        else {
            std::string prefix = std::string(data["prefix"].as_string());
            std::string commandId = std::string(data["commandId"].as_string());
            std::string machineGuid = std::string(data["machineGuid"].as_string());
            std::string taskType = std::string(data["taskType"].as_string());
            // Å¸ÀÓ½ºÅÆÇÁ

            SYSTEMTIME st;
            GetLocalTime(&st);

            json::object resp;

            resp["prefix"] = prefix;
            resp["commandId"] = commandId;
            resp["machineGuid"] = machineGuid;
            resp["taskType"] = taskType;
            std::string timestamp = FormatTime(st);
            if (prefix == "READ") {
                if (taskType == "START") {
                    if (logger_running) {
                        resp["payload"] = "ALREADY_RUNNING";
                    }
                    else {
                        logger_running = true;
                        resp["payload"] = "OK";
                    }
                }
                else if (taskType == "STOP") {
                    if (!logger_running) {
                        resp["payload"] = "ALREADY_STOPPED";
                    }
                    else {
                        logger_running = false;
                        resp["payload"] = "OK";
                    }
                }
                else {
                    resp["payload"] = "RESPONSE_ERROR";
                }
                conn_->send(json::serialize(resp));
            }
            else if (prefix == "HEARTBEAT") {

            }
        }
       
    }
}
