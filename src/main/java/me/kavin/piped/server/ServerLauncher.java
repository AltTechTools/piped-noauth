package me.kavin.piped.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import io.activej.config.Config;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;
import io.activej.launchers.http.MultithreadedHttpServerLauncher;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.server.handlers.*;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.resp.DeleteUserRequest;
import me.kavin.piped.utils.resp.LoginRequest;
import me.kavin.piped.utils.resp.StackTraceResponse;
import me.kavin.piped.utils.resp.SubscriptionUpdateRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executor;

import static io.activej.config.converter.ConfigConverters.ofInetSocketAddress;
import static io.activej.http.HttpHeaders.*;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ServerLauncher extends MultithreadedHttpServerLauncher {

    @Provides
    Executor executor() {
        return Multithreading.getCachedExecutor();
    }

    @Provides
    AsyncServlet mainServlet(Executor executor) {

        RoutingServlet router = RoutingServlet.create()
                .map(GET, "/healthcheck", AsyncServlet.ofBlocking(executor, request -> {
                    try (Session ignored = DatabaseSessionFactory.createSession()) {
                        return getRawResponse("OK".getBytes(UTF_8), "text/plain", "no-store");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/config", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(GenericHandlers.configResponse(), "public, max-age=86400");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                }))
                .map(GET, "/version", AsyncServlet.ofBlocking(executor, request -> getRawResponse(Constants.VERSION.getBytes(UTF_8), "text/plain", "no-store")))
                .map(HttpMethod.OPTIONS, "/*", request -> HttpResponse.ofCode(200))
                .map(GET, "/webhooks/pubsub", request -> HttpResponse.ok200().withPlainText(Objects.requireNonNull(request.getQueryParameter("hub.challenge"))))
                .map(POST, "/webhooks/pubsub", AsyncServlet.ofBlocking(executor, request -> {
                    try {

                        SyndFeed feed = new SyndFeedInput().build(
                                new InputSource(new ByteArrayInputStream(request.loadBody().getResult().asArray())));

                        Multithreading.runAsync(() -> {
                            for (var entry : feed.getEntries()) {
                                String url = entry.getLinks().get(0).getHref();
                                if (DatabaseHelper.getVideoFromId(StringUtils.substring(url, -11)) != null)
                                    continue;
                                VideoHelpers.handleNewVideo(url, entry.getPublishedDate().getTime(), null);
                            }
                        });

                        return HttpResponse.ofCode(204);

                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/sponsors/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SponsorBlockUtils.getSponsors(request.getPathParameter("videoId"),
                                        request.getQueryParameter("category")).getBytes(UTF_8),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/streams/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.streamsResponse(request.getPathParameter("videoId")),
                                "public, s-maxage=21540, max-age=30", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/clips/:clipId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.resolveClipId(request.getPathParameter("clipId")),
                                "public, max-age=31536000, immutable");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ChannelHandlers.channelResponse("channel/" + request.getPathParameter("channelId")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/c/:name", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ChannelHandlers.channelResponse("c/" + request.getPathParameter("name")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/user/:name", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ChannelHandlers.channelResponse("user/" + request.getPathParameter("name")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ChannelHandlers.channelPageResponse(request.getPathParameter("channelId"),
                                request.getQueryParameter("nextpage")), "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/channels/tabs", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        String nextpage = request.getQueryParameter("nextpage");
                        if (StringUtils.isEmpty(nextpage))
                            return getJsonResponse(ChannelHandlers.channelTabResponse(request.getQueryParameter("data")), "public, max-age=3600", true);
                        else
                            return getJsonResponse(ChannelHandlers.channelTabPageResponse(request.getQueryParameter("data"), nextpage), "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var playlistId = request.getPathParameter("playlistId");
                        var cache = StringUtils.isBlank(playlistId) || playlistId.length() != 36 ?
                                "public, max-age=600" : "private";
                        return getJsonResponse(me.kavin.piped.server.handlers.PlaylistHandlers.playlistResponse(playlistId), cache, true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                me.kavin.piped.server.handlers.PlaylistHandlers.playlistPageResponse(request.getPathParameter("playlistId"),
                                        request.getQueryParameter("nextpage")),
                                "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/rss/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getRawResponse(
                                me.kavin.piped.server.handlers.PlaylistHandlers.playlistRSSResponse(request.getPathParameter("playlistId")),
                                "application/atom+xml", "public, s-maxage=600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                    // TODO: Replace with opensearch, below, for caching reasons.
                })).map(GET, "/suggestions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(SearchHandlers.suggestionsResponse(request.getQueryParameter("query")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/opensearch/suggestions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SearchHandlers.opensearchSuggestionsResponse(request.getQueryParameter("query")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/search", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(SearchHandlers.searchResponse(request.getQueryParameter("q"),
                                request.getQueryParameter("filter")), "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/search", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SearchHandlers.searchPageResponse(request.getQueryParameter("q"),
                                        request.getQueryParameter("filter"), request.getQueryParameter("nextpage")),
                                "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/trending", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(TrendingHandlers.trendingResponse(request.getQueryParameter("region")),
                                "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.commentsResponse(request.getPathParameter("videoId")),
                                "public, max-age=1200", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.commentsPageResponse(request.getPathParameter("videoId"),
                                request.getQueryParameter("nextpage")), "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/registered/badge", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return HttpResponse.ofCode(302).withHeader(LOCATION, GenericHandlers.registeredBadgeRedirect())
                                .withHeader(CACHE_CONTROL, "public, max-age=30");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/", AsyncServlet.ofBlocking(executor, request -> HttpResponse.redirect302(Constants.FRONTEND_URL)));

        return new CustomServletDecorator(router);
    }

    @Override
    protected Module getOverrideModule() {
        return new AbstractModule() {
            @Provides
            Config config() {
                return Config.create()
                        .with("http.listenAddresses",
                                Config.ofValue(ofInetSocketAddress(), new InetSocketAddress(Constants.PORT)))
                        .with("workers", Constants.HTTP_WORKERS);
            }
        };
    }

    private @NotNull HttpResponse getJsonResponse(byte[] body, String cache) {
        return getJsonResponse(200, body, cache, false);
    }

    private @NotNull HttpResponse getJsonResponse(byte[] body, String cache, boolean prefetchProxy) {
        return getJsonResponse(200, body, cache, prefetchProxy);
    }

    private @NotNull HttpResponse getJsonResponse(int code, byte[] body, String cache) {
        return getJsonResponse(code, body, cache, false);
    }

    private @NotNull HttpResponse getJsonResponse(int code, byte[] body, String cache, boolean prefetchProxy) {
        return getRawResponse(code, body, "application/json", cache, prefetchProxy);
    }

    private @NotNull HttpResponse getRawResponse(byte[] body, String contentType, String cache) {
        return getRawResponse(200, body, contentType, cache, false);
    }

    private @NotNull HttpResponse getRawResponse(int code, byte[] body, String contentType, String cache,
                                                 boolean prefetchProxy) {
        HttpResponse response = HttpResponse.ofCode(code).withBody(body).withHeader(CONTENT_TYPE, contentType)
                .withHeader(CACHE_CONTROL, cache);
        if (prefetchProxy)
            response = response.withHeader(LINK, String.format("<%s>; rel=preconnect", Constants.IMAGE_PROXY_PART));
        return response;
    }

    private @NotNull HttpResponse getErrorResponse(Exception e, String path) {

        e = ExceptionHandler.handle(e, path);

        if (e instanceof ErrorResponse error) {
            return getJsonResponse(error.getCode(), error.getContent(), "private");
        }

        try {
            return getJsonResponse(500, Constants.mapper
                    .writeValueAsBytes(new StackTraceResponse(ExceptionUtils.getStackTrace(e), e.getMessage())), "private");
        } catch (JsonProcessingException ex) {
            return HttpResponse.ofCode(500);
        }
    }
}
