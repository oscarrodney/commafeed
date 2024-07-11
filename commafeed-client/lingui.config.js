module.exports = {
    locales: ["en", "es"], // Define the locales you want to support
    sourceLocale: "en", // Define your source locale
    catalogs: [
        {
            path: "<rootDir>/src/locales/{locale}/messages",
            include: ["<rootDir>/src"],
        },
    ],
    format: "po", // Define the format of the translation files
};