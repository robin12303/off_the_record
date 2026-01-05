#include "connection.h"

// Connection 역할:
// - Boost.Asio + Beast WebSocket으로 서버에 연결
// - async_resolve → async_connect → async_handshake
// - async_read로 계속 수신 (수신 메시지는 recv_q/recv_sem으로 넘김)
// - send()는 strand에 post해서 thread-safe하게 outq_에 쌓고 async_write로 순차 전송
//
// 핵심 포인트:
// - strand_를 사용해서 ws_/resolver_/outq_/open_에 대한 접근을 "단일 실행 흐름"으로 직렬화.
// - shared_from_this() 캡처로 비동기 작업이 실행되는 동안 객체 생존 보장.

Connection::Connection(asio::io_context& ioc)
    :
    // strand: 이 executor 위에서 수행되는 핸들러들은 서로 겹치지 않게 직렬 실행됨
    strand_(asio::make_strand(ioc)),

    // resolver_와 ws_ 둘 다 strand_에 바인딩해서 같은 직렬 컨텍스트에서 돌게 함
    resolver_(strand_),
    ws_(strand_)
{
}

void Connection::start(std::string host, std::string port, std::string target)
{
    // 접속 정보 저장 (move로 복사 비용 줄임)
    host_ = std::move(host);
    port_ = std::move(port);
    target_ = std::move(target);

    // 1) DNS resolve (비동기)
    resolver_.async_resolve(host_, port_,
        [self = shared_from_this()](
            beast::error_code ec,
            tcp::resolver::results_type results)
        {
            if (ec) return self->fail("resolve", ec);

            // 2) TCP connect (비동기)
            asio::async_connect(self->ws_.next_layer(), results,
                [self](beast::error_code ec, const tcp::endpoint&)
                {
                    if (ec) return self->fail("connect", ec);

                    // 3) WebSocket handshake (비동기)
                    // - host header에 "host:port"를 넣고, target 경로로 handshake
                    self->ws_.async_handshake(self->host_ + ":" + self->port_,
                        self->target_,
                        [self](beast::error_code ec)
                        {
                            if (ec) return self->fail("handshake", ec);

                            // 연결 오픈 표시
                            // ⚠️ open_을 다른 스레드에서 만진다면 atomic이 필요하지만,
                            //    이 코드에서는 보통 strand_ 안에서만 만지도록 설계하는 게 안전함.
                            self->open_ = true;

                            // 4) 읽기 루프 시작 (계속 재귀적으로 async_read)
                            self->do_read();

                            // handshake 전에 send()가 호출되어 outq_에 쌓인 데이터가 있으면 전송 시작
                            if (!self->outq_.empty())
                                self->do_write(); // 큐에 쌓인 거 있으면 시작
                        });
                });
        });
}

void Connection::send(std::string msg)
{
    // send는 어느 스레드에서 호출될지 모름.
    // 그래서 strand_에 post해서 outq_ 조작/쓰기 시작 여부 판단을 직렬화한다.
    asio::post(strand_,
        [self = shared_from_this(), msg = std::move(msg)]() mutable {

            // 정책: 연결이 아직 open_이 아니어도 outq_에 쌓아둠
            // - handshake 완료되면 start() 쪽에서 do_write()를 시작해줌
            // ⚠️ 주의: 연결이 장시간 안 열리면 outq_가 무한히 커질 수 있음.
            //          필요하면 최대 큐 길이 제한/드랍/백프레셔 정책을 넣는 게 일반적.
            self->outq_.push_back(std::move(msg));

            // open 상태이고, "막 0->1"로 들어온 순간에만 write 시작
            // (이미 write가 돌고 있으면 do_write()가 pop 후 다음을 이어서 처리함)
            if (self->open_ && self->outq_.size() == 1) {
                self->do_write();
            }
        });
}

websocket::stream<tcp::socket>& Connection::ws()
{
    // 외부에서 websocket stream이 필요할 때 접근자 제공
    return ws_;
}

asio::any_io_executor Connection::get_executor()
{
    // 외부에서 이 Connection의 실행 컨텍스트(executor)가 필요할 때 제공
    return ws_.get_executor();
}

void Connection::do_write()
{
    // outq_.front()를 비동기 write로 전송
    // ⚠️ async_write가 완료될 때까지 front()의 버퍼는 유효해야 함.
    //    여기서는 outq_에 저장된 std::string이 살아있으니 안전.
    ws_.async_write(asio::buffer(outq_.front()),
        [self = shared_from_this()](beast::error_code ec, std::size_t) {

            if (ec) return self->fail("write", ec);

            // 방금 보낸 메시지 제거
            self->outq_.pop_front();

            // 남아있으면 다음 메시지 이어서 전송 (순차 전송 보장)
            if (!self->outq_.empty())
                self->do_write();
        });
}

void Connection::do_read()
{
    // 비동기 read: inbuf_에 프레임을 누적해서 받아옴
    ws_.async_read(inbuf_,
        [self = shared_from_this()](beast::error_code ec, std::size_t) {

            if (ec) return self->fail("read", ec);

            // 받은 데이터를 문자열로 변환
            std::string msg_str = beast::buffers_to_string(self->inbuf_.data());

            // 버퍼 비우기 (다음 read를 위해)
            self->inbuf_.consume(self->inbuf_.size());

            // 받은 메시지를 전역 recv_q에 넣고, recv_sem으로 소비자에게 알림
            // ⚠️ recv_q/recv_sem은 producer-consumer 구조이므로,
            //    push/pop은 mutex로 보호하고, 개수 신호는 semaphore로 전달.
            {
                std::lock_guard<std::mutex> lk(recv_m);
                recv_q.push(std::move(msg_str));
            }
            recv_sem.release();

            // 읽기 루프 계속
            self->do_read();
        });
}

void Connection::fail(const char* what, beast::error_code ec)
{
    // 에러 로깅
    // ⚠️ 여기에 "연결 종료 처리(open_=false), outq_ 정리, 재연결 트리거" 같은 정책을 붙이는 경우가 많음.
    std::cerr << what << ": " << ec.message() << "\n";
}
