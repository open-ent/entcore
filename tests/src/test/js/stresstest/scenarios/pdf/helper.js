import { check } from "k6";
import {
  assertOk,
  getHeaders
} from "https://raw.githubusercontent.com/edificeio/edifice-k6-commons/develop/dist/index.js";
import { FormData } from "https://jslib.k6.io/formdata/0.0.2/index.js";
import http from "k6/http";

const dataRootPath = __ENV.DATA_ROOT_PATH || "./";
const rootUrl = __ENV.ROOT_URL;

export function createBlog(session, user, nbPosts) {
  const headers = getHeaders(session);
  const blogName = `Blog - Stress Test - ${user.email}`;
  headers["content-type"] = "application/json";
  const payload = JSON.stringify({
    title: blogName,
    description: `Le blog de ${user.login}`,
    thumbnail: "/blog/public/img/blog.png",
    "comment-type": "NONE",
    "publish-type": "RESTRAINT",
  });
  let res = http.post(`${rootUrl}/blog`, payload, { headers });
  assertOk(res, "create blog");
  const blogId = JSON.parse(res.body)["_id"];
  for (let i = 0; i < nbPosts; i++) {
    const postPayload = JSON.stringify({
      title: `Post ${String(i).padStart(5, "0")} de ${user.login}`,
      content: `Le contenu du super post ${String(i).padStart(
        5,
        "0"
      )} du blog de ${user.login}`,
    });
    res = http.post(`${rootUrl}/blog/post/${blogId}`, postPayload, { headers });
    //sleep(0.5)
    assertOk(res, "create post");
    const postId = JSON.parse(res.body)["_id"];
    if (postId) {
      res = http.get(
        `${rootUrl}/blog/post/list/all/${blogId}?postId=${postId}`,
        { headers }
      );
      assertOk(res, "get post");
      check(res, {
        "post was not found right after its creation": (r) => {
          const posts = JSON.parse(r.body);
          return posts.length && posts.length > 0;
        },
      });
      const resPublish = http.put(`${rootUrl}/blog/post/publish/${blogId}/${postId}`, {}, { headers });
      assertOk(resPublish, "publish post");
      check(resPublish, {
        "post was published": (r) => {
          return r.status === 200;
        },
      });
    }
  }
  return blogId;
}

export function publishBlog(blogId, session) {
  const title = "My Blog";
  const boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
  const description = "My Best Blog";
  const keyword = "CM2";
  const school = "My School";
  const minAge = 5, maxAge = 10;
  const printUrl = `${rootUrl}/blog/print/${blogId}`
  const application = 'Blog'
  const lang = "fr_FR"
  const activity = "bpr.activityType.homework";
  const subject = "bpr.subjectArea.worldDiscovery";
  const formData = `
${boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\n${title}\r\n
${boundary}\r\nContent-Disposition: form-data; name="cover"; filename="blob"\r\nContent-Type: image/jpeg\r\n\r\nÿØÿà\u0000\u0010JFIF\u0000\u00016·\u0000\u0000\u0000\u0000\r\n
${boundary}\r\nContent-Disposition: form-data; name="teacherAvatarName"\r\n\r\nteacherAvatar_b92e3d37-16b0-4ed9-b4c3-992091687132\r\n
${boundary}\r\nContent-Disposition: form-data; name="teacherAvatarType"\r\n\r\nimage/png\r\n
${boundary}\r\nContent-Disposition: form-data; name="language"\r\n\r\n${lang}\r\n
${boundary}\r\nContent-Disposition: form-data; name="activityType[]"\r\n\r\n${activity}\r\n
${boundary}\r\nContent-Disposition: form-data; name="subjectArea[]"\r\n\r\n${subject}\r\n
${boundary}\r\nContent-Disposition: form-data; name="age[]"\r\n\r\n${minAge}\r\n
${boundary}\r\nContent-Disposition: form-data; name="age[]"\r\n\r\n${maxAge}\r\n
${boundary}\r\nContent-Disposition: form-data; name="description"\r\n\r\n${description}\r\n
${boundary}\r\nContent-Disposition: form-data; name="keyWords[]"\r\n\r\n${keyword}\r\n
${boundary}\r\nContent-Disposition: form-data; name="licence"\r\n\r\nCC-BY\r\n
${boundary}\r\nContent-Disposition: form-data; name="pdfUri"\r\n\r\n${printUrl}\r\n
${boundary}\r\nContent-Disposition: form-data; name="application"\r\n\r\n${application}\r\n
${boundary}\r\nContent-Disposition: form-data; name="resourceId"\r\n\r\n${blogId}\r\n
${boundary}\r\nContent-Disposition: form-data; name="teacherSchool"\r\n\r\n${school}\r\n
${boundary}--\r\n`;
  const headers = getHeaders(session);
  headers["Content-Type"] = `multipart/form-data; boundary=${boundary}`;
  const res = http.post(`${rootUrl}/appregistry/library/resource`, formData, {
    headers: headers,
  });
  check(res, {
    "print blog ok": (r) => r.status === 201,
  });
}

export function printBlog(blogId, session) {
  const headers = getHeaders(session);
  headers['Content-Type'] = 'application/json';
  headers['Accept'] = 'application/json';
  const printUrl = `/blog/print/${blogId}`;

  const pdfUrl = `${rootUrl}/infra/pdf?name=print.pdf&url=${encodeURIComponent(printUrl)}`;
  const response = http.get(pdfUrl, { headers });

  check(response, {
    'print blog ok': (r) => r.status === 200,
  });

  return response;
}


const fileToUpload = open(`${dataRootPath}/pdf/file-sample_500kB.odt`, "b");
export function previewFile(session){
    const uploadedFile = uploadFile(fileToUpload, session)
    const res = http.get(`${rootUrl}/workspace/document/preview/${uploadedFile._id}`)
    check(res, {
      "preview odt ok": (r) => r.status === 200,
    });
}

function uploadFile(fileData, session) {
  let headers = getHeaders(session);
  const fd = new FormData();
  //@ts-ignore
  fd.append("file", http.file(fileData, "file.odt"));
  //@ts-ignore
  headers["Content-Type"] = "multipart/form-data; boundary=" + fd.boundary;
  let res = http.post(`${rootUrl}/workspace/document`, fd.body(), { headers });
  check(res, {
    "upload doc ok": (r) => r.status === 201,
  });
  return JSON.parse(res.body);
}