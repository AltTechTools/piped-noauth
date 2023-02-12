package me.kavin.piped.utils.obj;

public class StreamItem extends ContentItem {

//<<<<<<< HEAD
    public final String type = "stream";

//head    public String title, thumbnail, uploaderName, uploaderUrl, uploaderAvatar, uploadedDate, shortDescription;
    public String id, title, thumbnail, uploaderName, uploaderUrl, uploaderAvatar, uploadedDate, shortDescription;
//=======
//    public String id, url, title, thumbnail, uploaderName, uploaderUrl, uploaderAvatar, uploadedDate, shortDescription;
//>>>>>>> 4e6444c (Customisations)
    public long duration, views, uploaded;
    public boolean uploaderVerified, isShort;

    public StreamItem(String url, String title, String thumbnail, String uploaderName, String uploaderUrl,
//<<<<<<< HEAD
                      String uploaderAvatar, String uploadedDate, String shortDescription, long duration, long views, long uploaded, boolean uploaderVerified, boolean isShort) {
        super(url);
//=======
//                      String uploaderAvatar, String uploadedDate, String shortDescription, long duration, long views, long uploaded, boolean uploaderVerified) {
        this.id = url.replaceFirst("/watch\\?v=","");
//	this.url = url;
//>>>>>>> 4e6444c (Customisations)
        this.title = title;
        this.thumbnail = thumbnail;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatar = uploaderAvatar;
        this.uploadedDate = uploadedDate;
        this.shortDescription = shortDescription;
        this.duration = duration;
        this.views = views;
        this.uploaded = uploaded;
        this.uploaderVerified = uploaderVerified;
        this.isShort = isShort;
    }
}
