#include "WsConnection.h"
 
WsConnection::WsConnection(asio::io_context& ioc)
    :
    strand_(asio::make_strand(ioc)),
    resolver_(strand_),
    ws_(strand_)
{
}

void WsConnection::start(std::string host, std::string port, std::string target)
{
    host_ = std::move(host);
    port_ = std::move(port);
    target_ = std::move(target);

    resolver_.async_resolve(host_, port_,
        [self = shared_from_this()](
            beast::error_code ec,
            tcp::resolver::results_type results) {
                if (ec) return self->fail("resolve", ec);

                asio::async_connect(self->ws_.next_layer(), results,
                    [self](beast::error_code ec, const tcp::endpoint&) {
                        if (ec) return self->fail("connect", ec);

                        self->ws_.async_handshake(self->host_ + ":" +
                            self->port_,
                            self->target_,
                            [self](beast::error_code ec) {
                                if (ec) return self->fail("handshake", ec);
                                self->open_ = true;

                                self->do_read();
                                if (!self->outq_.empty())
                                    self->do_write(); // 큐에 쌓인 거 있으면 시작
                            });
                    });
        });
}

void WsConnection::send(std::string msg)
{
    asio::post(strand_, [self = shared_from_this(), msg = std::move(msg)]() mutable {
        self->outq_.push_back(std::move(msg));
        if (self->open_ && self->outq_.size() == 1) {
            self->do_write();
        }
        });
}

websocket::stream<tcp::socket>& WsConnection::ws()
{ 
    return ws_;
}

asio::any_io_executor WsConnection::get_executor()
{
    return ws_.get_executor();
}

void WsConnection::do_write()
{
    ws_.async_write(asio::buffer(outq_.front()),
        [self = shared_from_this()](beast::error_code ec, std::size_t) {
            if (ec) return self->fail("write", ec);
            self->outq_.pop_front();
            if (!self->outq_.empty()) self->do_write();
        });
}

void WsConnection::do_read()
{
    ws_.async_read(inbuf_,
        [self = shared_from_this()](beast::error_code ec, std::size_t) {
            if (ec) return self->fail("read", ec); 


            // 받은 메시지 처리 
            std::string s = beast::buffers_to_string(self->inbuf_.data());
            self->inbuf_.consume(self->inbuf_.size()); 

            json::object data = json::parse(s).as_object();

            std::string prefix = std::string(data["prefix"].as_string());
            std::string commandId = std::string(data["commandId"].as_string());
            std::string machineGuid = std::string(data["machineGuid"].as_string());
            std::string taskType = std::string(data["taskType"].as_string());


            std::cout << "prefix: " << prefix << "\n";
            std::cout << "commandId: " << commandId << "\n";
            std::cout << "machineGuid: " << machineGuid << "\n";
            std::cout << "taskType: " << taskType << "\n"; 

            if (prefix == "READ") {
                if (taskType == "START") { 
                }
            } 
            json::object resp;
            resp["prefix"] = prefix;
            resp["commandId"] = commandId;
            resp["machineGuid"] = machineGuid;
            resp["taskType"] = taskType; 
            resp["payload"] = "ACCEPTED";

            self->send(json::serialize(resp));

            self->do_read(); // 무한읽기


        });
}

void WsConnection::fail(const char* what, beast::error_code ec)
{
    std::cerr << what << ": " << ec.message() << "\n";
}


 