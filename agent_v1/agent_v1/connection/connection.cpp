#include "connection.h"

Connection::Connection(asio::io_context& ioc)
    :
    strand_(asio::make_strand(ioc)),
    resolver_(strand_),
    ws_(strand_)
{
}

void Connection::start(std::string host, std::string port, std::string target)
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

void Connection::send(std::string msg)
{
    asio::post(strand_,
        [self = shared_from_this(), msg = std::move(msg)]() mutable {
            // 연결이 열려있지 않으면: 정책 선택
            // 1) 버림
            // 2) 큐에 쌓아두고 나중에 handshake 끝나면 보내기(지금 구조는 이쪽)
            self->outq_.push_back(std::move(msg));

            if (self->open_ && self->outq_.size() == 1) {
                self->do_write(); // 지금 막 큐가 0->1 된 순간에만 write 시작
            }
        });
}


websocket::stream<tcp::socket>& Connection::ws()
{
    return ws_;
}

asio::any_io_executor Connection::get_executor()
{
    return ws_.get_executor();
}

void Connection::do_write()
{
    ws_.async_write(asio::buffer(outq_.front()),
        [self = shared_from_this()](beast::error_code ec, std::size_t) {
            if (ec) return self->fail("write", ec);
            self->outq_.pop_front();
            if (!self->outq_.empty()) self->do_write();
        });
}


void Connection::do_read()
{
    ws_.async_read(inbuf_,
        [self = shared_from_this()](beast::error_code ec, std::size_t) {
            if (ec) return self->fail("read", ec);

            std::string msg_str = beast::buffers_to_string(self->inbuf_.data());
            self->inbuf_.consume(self->inbuf_.size());

            {
                std::lock_guard<std::mutex> lk(recv_m);
                recv_q.push(std::move(msg_str));
            }
            recv_sem.release();

            self->do_read();
        });
}



void Connection::fail(const char* what, beast::error_code ec)
{
    std::cerr << what << ": " << ec.message() << "\n";
}


